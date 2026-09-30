module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/src"],
  // All suites share one Firestore emulator instance and the same hardcoded
  // projectId ("stroopoverload-test") -- running files in parallel workers
  // lets one file's afterEach(testEnv.clearFirestore()) wipe out data another
  // file just seeded mid-test. Force serial execution to keep suites isolated.
  maxWorkers: 1,
  // See test-support/jose-stub.js.
  moduleNameMapper: {
    "^jose$": "<rootDir>/test-support/jose-stub.js",
  },
};
