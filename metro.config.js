const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * The repo root also holds the Next.js web app (web/) plus docs and sandbox
 * folders. Keep Metro's module resolution AND its file watcher (the crawl
 * respects blockList) out of everything that is not the React Native app —
 * otherwise the watcher blows past the OS inotify limit on low-limit hosts.
 *
 * @type {import('metro-config').MetroConfig}
 */

const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const blockDir = (dir) => new RegExp('^' + esc(__dirname + '/' + dir) + '/');
/* Android/Gradle build artifacts inside node_modules — never part of the JS
   graph, but each package's android/build dir adds thousands of inotify
   watches on low-limit hosts (ENOSPC during Metro's crawl). */
const blockPattern = (p) => new RegExp(esc(__dirname + '/node_modules/') + p);

const config = {
  resolver: {
    blockList: [
      blockDir('web'), // the Next.js web app (has its own node_modules)
      blockDir('docs'),
      blockDir('examples'),
      blockDir('tests'),
      blockDir('skills'),
      blockDir('upload'),
      blockPattern('[^/]+/android/build/'), // gradle outputs of RN native deps
      blockPattern('[^/]+/android/build/[^/]+/'), // nested (intermediates etc.)
      /\/android\/build\/intermediates\//, // any depth gradle intermediates
      /\/\.git\//,
      /\/\.gradle\//,
    ],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
