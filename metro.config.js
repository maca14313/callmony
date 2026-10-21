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

const config = {
  resolver: {
    blockList: [
      blockDir('web'), // the Next.js web app (has its own node_modules)
      blockDir('docs'),
      blockDir('examples'),
      blockDir('tests'),
      blockDir('skills'),
      blockDir('upload'),
    ],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
