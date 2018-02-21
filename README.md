# duct-microservices
Duct module for creating microservices

## TODO

- [x] configuration for launching multiple microservices (jetty servers on different ports)
- []  separate migrator configuration for each microservice
- []  handle Zookeper discoverability

## Usage

To add module to your configuration, add the `:duct.module.web/microservices` key:

```clojure
{:duct.module.web/microservices {}}
```

Now we can specify 3 kinds of microservices (similar to duct module.web):

* :duct.microservice/web
* :duct.microservice/api
* :duct.microservice/site

Each microservice is defined by composite key (type and microservice id), for instance:

```clojure
{[:duct.microservice/api  :system/s1] {}
 [:duct.microservice/api  :system/s2] {}
 [:duct.microservice/site :system/s2] {}}
```

Module will create configuration for each defined microservice.  
Configurations will be constructed using composite keys.  
You can customize each microservice configuration by those keys, for instance:

```clojure

{[:duct.server.http/jetty :system/s1] {:port 3005}
 [:duct.server.http/jetty :system/s2] {:port 3006}}
```

## License

Copyright © 2018 Mariusz Jachimowicz

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
