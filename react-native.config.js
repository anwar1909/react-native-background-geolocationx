const path = require('path');

module.exports = {
  dependencies: {
    '@anwar1909/react-native-background-geolocationx': {
      root: __dirname,
      platforms: {
        android: {
          sourceDir: path.join(__dirname, 'android', 'background-geolocation'),
          libraryName: 'anwar1909_react-native-background-geolocationx',
          packageImportPath: 'import com.anwar1909.bgloc.react.BackgroundGeolocationPackage;'
        },
      },
    },
  },
};