# Datomic Intro

A small collection of different things you can do with datomic. Requires a running
transactor and peer server that you can connect to.

## Dependencies

- [Clojure command line tools](https://clojure.org/guides/deps_and_cli).
- [get a datomic starter license](https://www.datomic.com/get-datomic.html)
- [datomic local dev setup](https://docs.datomic.com/on-prem/getting-started/dev-setup.html)

Make sure you follow the steps for connecting the `client library` to a `peer server`,
**not** the `peer library`.

The code in this repo connects to a local peer server. If you want to experiment
with connecting to another endpoint, or using in-memory data storage, or any other
client library configuration, you'll need to make changes to the code in
`datomic-demo.data`.

## Resources

- [presentation slides](https://docs.google.com/presentation/d/1H02T4QLVtuywhafZ4jLKTnyJuRO5AzNkHBo5YzAZyGY/edit#slide=id.g128fa81bab3_0_74)
