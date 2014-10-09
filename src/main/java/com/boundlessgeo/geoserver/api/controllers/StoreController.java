/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Files;
import org.geoserver.platform.resource.Paths;
import org.geotools.data.DataAccess;
import org.geotools.data.FeatureSource;
import org.geotools.data.ServiceInfo;
import org.geotools.data.ows.Layer;
import org.geotools.data.wms.WebMapServer;
import org.geotools.util.Converters;
import org.geotools.util.NullProgressListener;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.common.base.Throwables;

/**
 * Used to connect to data storage (file, database, or service).
 * <p>
 * This API is locked down for map composer and is (not intended to be stable between releases).</p>
 * 
 * @see <a href="https://github.com/boundlessgeo/suite/wiki/Stores-API">Store API</a> (Wiki)
 */
 @Controller
 @RequestMapping("/api/stores")
 public class StoreController extends ApiController {
     static Logger LOG = Logging.getLogger(StoreController.class);

    @Autowired
    public StoreController(GeoServer geoServer) {
        super(geoServer);
    }

    @RequestMapping(value = "/{wsName}", method = RequestMethod.GET)
    public @ResponseBody
    JSONArr list(@PathVariable String wsName){
        JSONArr arr = new JSONArr();
        Catalog cat = geoServer.getCatalog();
        for (StoreInfo store : cat.getStoresByWorkspace(wsName, StoreInfo.class)) {
            store(arr.addObject(), store);
        }
        return arr;
    }
    
    @RequestMapping(value = "/{wsName}/{name}", method = RequestMethod.GET)
    public @ResponseBody
    JSONObj get(@PathVariable String wsName,
                @PathVariable String name){
        if (name == null) {
            throw new IllegalArgumentException("Store name required");
        }
        Catalog cat = geoServer.getCatalog();
        StoreInfo store = cat.getStoreByName(wsName, name, StoreInfo.class);
        if( store == null ){
            throw new IllegalArgumentException("Store "+wsName+":"+name+" not found");
        }
        JSONObj obj = storeDetails(new JSONObj(), store);

        return obj;
    }

    public enum Type {FILE,DATABASE,WEB,GENERIC;
        static Type of( StoreInfo store ){
            if( store instanceof CoverageStoreInfo){
                String url = ((CoverageStoreInfo)store).getURL();
                if( url.startsWith("file")){
                    return Type.FILE;
                }
                else if( url.startsWith("http") ||
                         url.startsWith("https") ||
                         url.startsWith("ftp") ||
                         url.startsWith("sftp")){
                    return Type.WEB;
                }
            }
            Map<String, Serializable> params = store.getConnectionParameters();
            if( params.containsKey("dbtype")){
                return Type.DATABASE;
            }
            if( store instanceof WMSStoreInfo){
                return Type.WEB;
            }
            if( params.keySet().contains("directory") ||
                params.keySet().contains("file") ){
                
                return Type.FILE;
            }
            for( Object value : params.values()){
                if( value == null ) continue;
                if( value instanceof File ||
                    (value instanceof String && ((String)value).startsWith("file:")) ||
                    (value instanceof URL && ((URL)value).getProtocol().equals("file"))){
                    return Type.FILE;
                }
                if( (value instanceof String && ((String)value).startsWith("http:")) ||
                    (value instanceof URL && ((URL)value).getProtocol().equals("http"))){
                    return Type.WEB;
                }
                if( value instanceof String && ((String)value).startsWith("jdbc:")){
                    return Type.DATABASE;
                }
            }
            return Type.GENERIC;
        }        
    }
    public enum Kind {RASTER,VECTOR,SERVICE,UNKNOWN;
        static Kind of( StoreInfo store ){
            if( store instanceof CoverageStoreInfo){
                return Kind.RASTER;
            }
            else if( store instanceof DataStoreInfo){
                return Kind.VECTOR;
            }
            else if(store instanceof WMSStoreInfo){
                return Kind.SERVICE;
            }
            return Kind.UNKNOWN;
        }
    }
    /**
     * Complete store details.
     * <ul>
     * <li>name,workspace,description,format</li>
     * <li>display: source, type, kind, workspace</li>
     * <li>metadata: author, created, changed</li>
     * </ul>
     * 
     * @param json json
     * @param store store
     * @return store details including connection parameters, errors and id
     */
    JSONObj store(JSONObj obj, StoreInfo store) {       
        String name = store.getName();

        obj.put("name", name)
                .put("workspace", store.getWorkspace().getName())
                .put("description", store.getDescription()).put("enabled", store.isEnabled())
                .put("format", store.getType());
        
        String source = source(store);
        obj.put("source", source )
           .put("type", Type.of(store).name())
           .put("kind", Kind.of(store).name());   

        JSONObj metadata = IO.metadata( new JSONObj(), store.getMetadata() );
        obj.put("metadata",metadata);
        
        
        return obj;
    }
    /**
     * Complete store details.
     * <ul>
     * <li>id</li>
     * <li>connection: connection parameters (includes raster url or wms url)</li>
     * <li>error: message and trace</li>
     * </ul>
     * 
     * @param json json
     * @param store store
     * @return store details including connection parameters, errors and id
     */
    JSONObj storeDetails(JSONObj json, StoreInfo store) {
        store(json, store);

        JSONObj connection = new JSONObj();
        Map<String, Serializable> params = store.getConnectionParameters();
        for( Entry<String,Serializable> param : params.entrySet() ){
            String key = param.getKey();
            Object value = param.getValue();
            String text = value.toString();
            
            connection.put( key, text );
        }
        if (store instanceof CoverageStoreInfo) {
            CoverageStoreInfo info = (CoverageStoreInfo) store;
            connection.put("raster", info.getURL());
        }
        if (store instanceof WMSStoreInfo) {
            WMSStoreInfo info = (WMSStoreInfo) store;
            json.put("wms", info.getCapabilitiesURL());
        }
        json.put("connection", connection );
        
        Throwable error = store.getError();
        if (error != null) {
            json.putObject("error")
                    .put("message", error.getMessage())
                    .put("trace", Throwables.getStackTraceAsString(error));
        }
        if( store.isEnabled()){
            try {
                JSONArr content = contents( store );
                json.put("contents", content );
            } catch (IOException e) {
                LOG.log(Level.FINEST, e.getMessage(), e );
            }
        }
        json.put("layers", resources( store ));
        
        return json;
    }
    
    private JSONArr contents(StoreInfo store) throws IOException {
        if (store instanceof DataStoreInfo) {
            return contents((DataStoreInfo) store);
        }
        if (store instanceof CoverageStoreInfo) {
            return contents((CoverageStoreInfo) store);
        }
        if (store instanceof WMSStoreInfo) {
            return contents((WMSStoreInfo) store);
        }
        return null;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private JSONArr contents(DataStoreInfo data) {
        JSONArr l = new JSONArr();
        List<Name> names;
        DataAccess dataStore;
        try {
            dataStore = data.getDataStore(new NullProgressListener());
            names = dataStore.getNames();
        } catch (IOException e) {
            LOG.log(Level.FINE,data.getId()+" unable list contents:"+e,e);
            return l; 
        }
        for( Name n : names){
            JSONObj r = l.addObject()
                .put("name", n.getLocalPart() );
            
            try {
                FeatureType schema = dataStore.getSchema(n);
                r.put("geometry", IO.geometry(schema));
            } catch (IOException e) {
                LOG.log(Level.FINER,n+" unable to determine FeatureType:"+e);
            }
            
            try {
                FeatureSource fs = dataStore.getFeatureSource(n);
                org.geotools.data.ResourceInfo info = fs.getInfo();
                
                r.put("title",info.getTitle() )
                 .put("description",info.getDescription() );
                
                //r.put("keywords",IO.arr(info.getKeywords()) );
            } catch (IOException e) {
                LOG.log(Level.FINER,n+" unable to access Features:"+e);
            }
        }
        return l;
    }

    private JSONArr contents(CoverageStoreInfo raster) {
        JSONArr l = new JSONArr();
        GridCoverageReader reader;
        String[] coverageNames;
        try {
            reader = raster.getGridCoverageReader(new NullProgressListener(), null);
            coverageNames = reader.getGridCoverageNames();
        } catch (IOException e) {
            LOG.log(Level.FINE,raster.getId()+" unable list contents:"+e,e);
            return l; 
        }
        if (coverageNames.length != 0) {
            for (String n : coverageNames) {
                l.addObject()
                        .put("name", n)
                        .put("geometry","raster");
            }
        }
        else {
            l.addObject()
                .put("name", "GridCoverage") // See FeatureUtilities.wrapGridCoverage
                .put("geometry","raster");
        }
        return l;
    }
    private JSONArr contents(WMSStoreInfo wms) {
        JSONArr l = new JSONArr();
        WebMapServer service;
        List<Layer> layers;
        try {
            service = wms.getWebMapServer(new NullProgressListener());
            layers = service.getCapabilities().getLayerList();
        } catch (IOException e) {
            LOG.log(Level.FINE,wms.getId()+" unable list contents:"+e,e);
            return l; 
        }
        for (Layer layer : layers) {
            l.addObject()
                .put("name", layer.getName())
                .put("geometry","layer");
        }
        return l;
    }
    
    private JSONArr resources(StoreInfo store) {
        JSONArr arr = new JSONArr();
        Catalog cat = geoServer.getCatalog();
        List<ResourceInfo> resources = cat.getResourcesByStore(store, ResourceInfo.class );
        for( ResourceInfo r : resources ){
            for (LayerInfo l : cat.getLayers(r)) {
                JSONObj layer = arr.addObject();                
                
                layer.put("name", l.getName())
                        .put("title", l.getTitle() != null ? l.getTitle() : r.getTitle())
                        .put("description", l.getAbstract() != null ? l.getAbstract() : r.getAbstract())
                        .put("type", IO.type(r));
                JSONObj metadata = IO.metadata( new JSONObj(), l.getMetadata());
                layer.put( "metadata", metadata );                
                layer.put("content", r.getNativeName() );
            }
        }        
        return arr;
    }
    
    /** 
     * Storage source.
     * <p>
     * This method is based on a revise engineering of {@link ServiceInfo#location}.
     *  
     * @param store
     * @return
     */
    private String source(StoreInfo store) {        
        if( store instanceof CoverageStoreInfo ){
            CoverageStoreInfo coverage = (CoverageStoreInfo) store;
            return sourceURL( coverage.getURL() );
        }
        GeoServerResourceLoader resourceLoader = geoServer.getCatalog().getResourceLoader();
        Map<String, Serializable> params =
                ResourcePool.getParams( store.getConnectionParameters(), resourceLoader );
        if( params.containsKey("dbtype")){
            // See JDBCDataStoreFactory for details
            String host = Converters.convert(params.get("host"),  String.class);
            String port = Converters.convert(params.get("port"),  String.class);
            String dbtype = Converters.convert(params.get("dbtype"),  String.class);
            String schema = Converters.convert(params.get("schema"),  String.class);
            String database = Converters.convert(params.get("database"),  String.class);
            StringBuilder source = new StringBuilder();
            source.append(host);
            if( port != null ){
                source.append(':').append(port);
            }
            source.append('/').append(dbtype).append('/').append(database);
            if( schema != null ){
                source.append('/').append(schema);
            }
            return source.toString();
        }
        else if( store instanceof WMSStoreInfo){
            String url = ((WMSStoreInfo)store).getCapabilitiesURL();
            return url;
        }
        else if( params.keySet().contains("directory")){
            String directory = Converters.convert(params.get("directory"),String.class);
            return sourceFile( directory );
        }
        else if( params.keySet().contains("file")){
            String file = Converters.convert(params.get("file"),String.class);
            return sourceFile( file );
        }
        if( params.containsKey("url")){
            String url = Converters.convert(params.get("url"),String.class);
            return sourceURL( url );
        }
        for( Object value : params.values() ){
            if( value instanceof URL ){
                return source( (URL) value );
            }
            if( value instanceof File ){
                return source( (File) value );
            }
            if( value instanceof String ){
                String text = (String) value;
                if( text.startsWith("file:")){
                    return sourceURL( text );
                }
                else if ( text.startsWith("http:") || text.startsWith("https:") || text.startsWith("ftp:")){
                    return text;
                }
            }
        }
        return "undertermined";
    }
    
    String source( File file ){
        File baseDirectory = dataDir().getResourceLoader().getBaseDirectory();
        return file.isAbsolute() ? file.toString() : Paths.convert(baseDirectory,file);
    }
    String source( URL url ){
        File baseDirectory = dataDir().getResourceLoader().getBaseDirectory();
        
        if( url.getProtocol().equals("file")){
            File file = Files.url(baseDirectory,url.toExternalForm());
            if( file != null && !file.isAbsolute() ){
                return Paths.convert(baseDirectory, file); 
            }
        }
        return url.toExternalForm();
    }
    String sourceURL( String  url ){
        File baseDirectory = dataDir().getResourceLoader().getBaseDirectory();

        File file = Files.url(baseDirectory,url);
        if( file != null ){
            return Paths.convert(baseDirectory, file); 
        }
        return url;
    }
    String sourceFile( String file ){
        File baseDirectory = dataDir().getResourceLoader().getBaseDirectory();

        File f = new File( file );
        return f.isAbsolute() ? file : Paths.convert(baseDirectory,f);
    }
}
