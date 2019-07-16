module.exports = {
  setupFilesAfterEnv: ['./src/setup-test.js'],
  modulePaths: ['./jest'],
  moduleNameMapper: {
    '\\.(jpg|jpeg|png|gif|eot|otf|webp|svg|ttf|woff|woff2|mp4|webm|wav|mp3|m4a|aac|oga)$': 'file-mock.js',
  },
};
