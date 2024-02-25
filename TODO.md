* Eliminate source headers
* Revise / update documentation
  - Especially threading and connection testing
* Make loom warning about context classloader / privilege threads conditional on those actually being requested.
* Maybe optimize away effectiveStatementCache field from C3P0PooledConnectionPool.
