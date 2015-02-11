[![Build Status](https://magnum.travis-ci.com/iHealthTechnologies/adda.svg?token=CJFut42zn19H1aBG2n3Q)](https://magnum.travis-ci.com/iHealthTechnologies/adda)

# Adda
Adda is a publish/subscribe layer on top of a triple store.

TODO: Describe in more detail.

# Troubleshooting

```
Exception: sbt.ResolveException: unresolved dependency: com.esotericsoftware#kryo;3.0.0: Artifactory Realm: unable to get resource for com/esotericsoftware#kryo;3.0.0: res=null/libs-snapshots-local/com/esotericsoftware/kryo/3.0.0/kryo-3.0.0.pom: java.net.MalformedURLException: no protocol: null/libs-snapshots-local/com/esotericsoftware/kryo/3.0.0/kryo-3.0.0.pom
```

Resolution: Ask someone from DevOps for artifactory credentials and put them into `~/.bash_profile'.
