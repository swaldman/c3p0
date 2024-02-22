* Change defaults? Or just document simple recommendations?
  - Use large-but-nonzero default for `maxAdminstrativeTaskTime`? (And change docs! Also, note in migration notes)
  - Set `testConnectionOnCheckout` to `true` by default (Big migration note!)
* Eliminate deprecated, unmaintained PoolConfig
* Eliminate traditional reflective proxies
* Eliminate source headers
* Connection testing revamp
  - Implement connectionIsValidTimeout
  - Break testing out into separate NullConnectionTester and ConnectionTester paths
  - Simplify docs for default NullConnectionTesterPath, remove existing docs to an appendix


