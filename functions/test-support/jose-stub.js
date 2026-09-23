// firebase-admin v14 pulls in jose (ESM-only) through jwks-rsa, for verifying
// ID/App Check tokens. Jest runs these suites as CommonJS and cannot parse it.
// Nothing here ever verifies a real token -- handlers are invoked directly via
// .run() with a hand-built request -- so jose is stubbed out entirely. If a suite
// ever does need real token verification, this mapping has to go and Jest needs
// real ESM support instead.
module.exports = new Proxy(
  {},
  {
    get() {
      throw new Error("jose is stubbed in tests: no suite should verify real tokens (see test-support/jose-stub.js)");
    },
  }
);
