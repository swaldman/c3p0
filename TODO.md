* Change defaults?
  - Use large-but-nonzero default for `maxAdminstrativeTaskTime`? (And change docs! Also, not in migration notes)T
  - Set `testConnectionOnCheckout` to `true` by default (Big migration note!)
* New docs build
* Implement new timeout properties (pass-through as properties to drivers that support).
  - `connectionTimeout` (default 10 secs? or -1 to not set?)
  - `loginTimeout` (default 20 secs? or -1 to not set?)
* c3p0-loom



