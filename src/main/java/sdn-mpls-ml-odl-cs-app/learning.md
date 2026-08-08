# Learning Documentation
> This is an additional file of knowledge, not a knowledge base that I have the project for, but it describes some 
> of the things I want to write down when working.

## What is OSGi and how does it work alongside ODL?

Opendaylight as an application, is built on top of Apache Karaf, which is described as a Monulith system that allows an 
application to be built as a set of bundles. Apache Karaf works as a lightweight OSGi-based runtime contianer which allows 
multiple modules, or bundles to be hot reloaded, loaded, updated, installed, and managed. This provides a more `enterprise grade`
set of tools for running applications that are built in Java and are Modular.

This application extends conventional OSGi frameworks like Apache Felix or Eclipse Equinox, with additional features like 
**logging, dynamic feature management, deployment mechanisms, security, and management interfaces**. This makes it a huge 
improvement over having everything loaded at once for applications like an SDN controller where you can now install features
on demand.

### What is OSGi? 

> OSGi or Open Service Gateway Initiative is an extension over normal Java packages and modules that allows developers to create **robust 
>, highly decoupled and dynamic applications in Java. 