angular.module('gsApp.sidenav', [
  'gsApp.workspaces.workspace',
  'ui.bootstrap',
  'gsApp.olmap'
])
.directive('sidenav', function() {
    return {
      restrict: 'EA',
      templateUrl: '/components/sidenav/sidenav.tpl.html',
      controller: 'SideNavCtrl',
      replace: true
    };
  })
.controller('SideNavCtrl', ['$scope', '$rootScope', 'GeoServer',
  'AppEvent', '$state', '$log', '$timeout', '$window',
  function($scope, $rootScope, GeoServer, AppEvent, $state, $log,
    $timeout, $window) {

    // Hug partial menu to sidebar bottom if height's enough
    $scope.onWindowResize = function() {
      var windowHeight = $window.innerHeight - 150;
      if (windowHeight < 300) {
        $scope.sideStyle = {'position': 'relative'};
        $scope.sideBottom = {'position': 'relative'};
      } else {
        $scope.sideStyle = {'position': 'absolute'};
        $scope.sideBottom = {'top': (windowHeight-40) + 'px'};
      }
    };
    $scope.onWindowResize();
    var timer = null;
    $(window).resize(function() { // angular $window checked too often
      if (timer===null) {
        timer = $timeout(function() {
          $scope.onWindowResize();
          timer = null;
        }, 700);
      }
    });

    $scope.onResize = function() {
      $rootScope.$broadcast(AppEvent.SidenavResized);
    };

    $scope.onWorkspaceClick = function(workspace, detail) {
      if (detail) {
        $state.go('workspace.home.' + detail, {
          workspace: workspace.name
        });
      } else {
        $state.go('workspace.home', {
          workspace: workspace.name
        });
      }
    };

    $scope.newWorkspace = function() {
      $state.go('workspace.new', {'workspaces': $scope.workspaces});
    };

    $rootScope.$on(AppEvent.WorkspacesFetched,
      function(scope, workspaces) {
        $scope.workspaces = workspaces;
      });

    $rootScope.$on(AppEvent.WorkspaceNameChanged,
      function(scope, names) {
        $scope.workspaces.forEach(function(workspace) {
          if (workspace.name ===  names.original) {
            workspace.name = names.new;
            return;
          }
        });
      });

    $rootScope.$on(AppEvent.WorkspaceDeleted,
      function(scope, deletedSpaceName) {
        for (var p=0; p < $scope.workspaces.length; p++) {
          if ($scope.workspaces[p].name === deletedSpaceName) {
            $scope.workspaces.splice(p,1);
          }
        }
      });

    $rootScope.$on(AppEvent.ToggleSidenav,
      function(scope) {
        if (!$scope.toggleSide) {
          $scope.toggleSide = true;
          $timeout(function() {
            $scope.onResize();
          },450);
        }
      });

    $rootScope.$on(AppEvent.ServerError,
      function(scope, error) {
        $scope.alerts.push({
          type: 'error',
          message: 'Server not responding ' + error.status + ': ' +
           error.data,
          fadeout: true
        });
      });
  }]);

