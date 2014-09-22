/* jshint maxlen: 120 */
angular.module('gsApp.layers', [
  'ngGrid',
  'ui.select',
  'gsApp.layers.style'
])
.config(['$stateProvider',
    function($stateProvider) {
      $stateProvider
        .state('layers', {
          url: '/layers',
          templateUrl: '/layers/layers.tpl.html',
          controller: 'LayersCtrl'
        })
        .state('layer', {
          abstract: true,
          url: '/layers/:workspace/:name',
          templateUrl: '/layers/detail/layer.tpl.html'
        });
    }])
.directive('layerType', function() {
  return {
    restrict: 'A',
    replace: true,
    transclude: true,
    scope: { type: '@type', geometry: '@geometry' },
    templateUrl: '/layers/layers.type.tpl.html'
  };
})
.controller('LayersCtrl', ['$scope', 'GeoServer', '$state', '$log',
    function($scope, GeoServer, $state, $log) {
      $scope.title = 'All Layers';

      $scope.onStyleEdit = function(layer) {
        $state.go('layer.style', {
          workspace: layer.workspace,
          name: layer.name
        });
      };
      $scope.pagingOptions = {
        pageSizes: [25, 50, 100],
        pageSize: 25
      };
      $scope.gridSelections = [];
      $scope.gridOptions = {
        data: 'layerData',
        columnDefs: [{
          field: '<div ng-class="text-center col.colIndex()"><input type="checkbox" /></div>',
          displayName: '',
          cellTemplate: '<div ng-class="col.colIndex()"><input type="checkbox" /></div>',
          width: 25
        }, {
          field: 'name',
          displayName: 'Layer',
          width: 250
        }, {
          field: 'title',
          displayName: 'Title',
          width: 250
        }, {
          field: 'type',
          displayName: 'Type',
          cellClass: 'text-center',
          cellTemplate:
            '<div layer-type type="{{row.entity.type}}" geometry="{{row.entity.geometry}}"></div>',
          width: 50
        }, {
          field: 'srs',
          displayName: 'SRS',
          cellClass: 'text-center',
          cellTemplate: '{{row.entity.proj.srs}}',
          width: 150
        }, {
          field: 'settings',
          displayName: 'Settings',
          cellClass: 'text-center',
          cellTemplate:
            '<div ng-class="col.colIndex()">'+
              '<a ng-click="onStyleEdit(row.entity)">'+
                '<img ng-src="images/settings.png" alt="Edit Layer Settings" title="Edit Layer Settings" />'+
              '</a>' +
           '</div>',
          width: 75
        }, {
          field: 'style',
          displayName: 'Styles',
          cellClass: 'text-center',
          cellTemplate:
            '<li class="list-unstyled dropdown">' +
              '<a href="#" class="dropdown-toggle" data-toggle="dropdown">' +
                '<div class="grid-text">Edit</div>' +
                '<img ng-src="images/edit.png" alt="Edit Style" title="Edit Style" />' +
              '</a>' +
              '<ul id="style-dropdown" class="dropdown-menu">' +
                '<li><a ng-click="onStyleEdit(row.entity)">Style 1</a></li>' +
                '<li><a ng-click="onStyleEdit(row.entity)">Style 2</a></li>' +
                '<li><a class="add-new-style" ng-click="#">Add New Style</a></li>' +
              '</ul>' +
            '</li>',
          width: 75
        }, {
          field: 'download',
          displayName: 'Download',
          cellClass: 'text-center',
          cellTemplate:
            '<li class="list-unstyled dropdown">' +
              '<a href="#" class="dropdown-toggle" data-toggle="dropdown">' +
                '<img ng-src="images/download.png" alt="Download Layer" title="Download Layer" />' +
              '</a>' +
              '<ul id="download-dropdown" class="dropdown-menu">' +
                '<li><a href="#">Shapefile</a></li>' +
                '<li><a href="#">GeoJSON</a></li>' +
                '<li><a href="#">KML</a></li>' +
              '</ul>' +
            '</li>',
          width: 75
        }, {
          field: 'preview',
          displayName: 'Preview',
          cellClass: 'text-center',
          cellTemplate:
            '<div ng-class="col.colIndex()">' +
              '<a ng-click="onStyleEdit(row.entity)">'+
                '<img ng-src="images/preview.png" alt="Preview Layer" title="Preview Layer" />' +
              '</a>'+
            '</div>',
          width: 75
        }, {
          field: 'lastEdited',
          displayName: 'Last Edited',
          cellTemplate: '<div ng-class="col.colIndex()"></div>',
          width: 100
        }, {
          field: '',
          displayName: '',
          cellClass: 'text-center',
          cellTemplate:
            '<div ng-class="col.colIndex()">' +
              '<a ng-click="onDeleteStyle(row.entity)">'+
                '<img ng-src="images/delete.png" alt="Remove Layer" title="Remove Layer" />' +
              '</a>' +
            '</div>',
          width: 30
        }, {
          field: '',
          displayName: '',
          width: 30
        }],
        enablePaging: true,
        enableColumnResize: false,
        showFooter: true,
        pagingOptions: $scope.pagingOptions,
        filterOptions: {
          filterText: '',
          useExternalFilter: true
        }
      };

      $scope.workspace = {};
      $scope.workspaces = [];

      $scope.workspaceChanged = function(ws) {
        GeoServer.layers.get({workspace: ws.name}).$promise
          .then(function(layers) {
            $scope.layerData = layers;
          });
      };

      GeoServer.workspaces.get().$promise.then(function(workspaces) {
        workspaces.filter(function(ws) {
          return ws['default'];
        }).forEach(function(ws) {
          $scope.workspace.selected = ws;
          $scope.workspaceChanged(ws);
        });

        $scope.workspaces = workspaces;
      });
    }]);
