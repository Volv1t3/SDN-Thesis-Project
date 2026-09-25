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
>, highly decoupled, and dynamic applications in Java.** This framework is part of a standarization effort which allows to 
> write applications that need to support notifications for service degradation and overall inform other applications
> of their services, bundle status, etc.

OSGi functions on the bases of Bundles, these are the building blocks that define
1. Code 
2. Resources 
3. Metadata

It is through Bundles that applications  are executed, installed, started and stopped inside a OSGi based runtime like
Apache Karaf. A bundle in this sense has an `independent lifecycle`, which means that it defines functions such as 
`start`, `stop`, etc., that allow it to be registered, installed and to be useful in an environment where hot reloading is 
supposed to be the norm. 

A bundle is defined as a `Java JAR with an additional MANIFEST.MF` file which defines additional headers proposed and 
standardized by the OSGi Alliance, these fields allow the application developer to tell to the OSGi runtime which functions 
to, for example, call when the application is started, stopped, etc.

The base definition of a bundle is based on this 

```Java
public class HelloWorld implements BundleActivator {
    public void start(BundleContext ctx) {
        System.out.println("Hello world.");
    }
    public void stop(BundleContext bundleContext) {
        System.out.println("Goodbye world.");
    }
}

```

Notice how in this case, none of the POM.xml files for the project declare  any of these imports. However, the system understands 
the required OSGi information and based on that it creates the required .MF files and the features.xml files
which can be used to define requirements of installation in the ODL controller


### What is Apache Karaf? 

> Apache Karaf is a monulith runtime for OSGi, it is built upon Apache Felix which is Apache's implementation of OSGi. This 
> is the runner of the applications, and it is used by Opendaylight as the base system underneath for its controller, hence
> why this section is in both Java, under Maven and with a focus on OSGi compatibility.

Apache Karaf is a runtime that provides additional features:
1. A lightweight container for OSGi bundles 
2. A feature mechanism for bundle management 
3. A configuration system based on ConfigAdmin 
4. A command console for runtime interaction

This allows it to be a much more developer friendly environment as well as a simpler distribution mechanism as features can 
be installed, or uninstalled depending on the actual requirements of a controller, in our case we install often PCEP, BGPCEP,
Openflow and NETCONF and RESTCONF components for it to work in our use case.

The relationship between these components is defined as 

```text
Java class
  ↓ compiled into
OSGi bundle JAR
  ↓ listed inside
Karaf feature XML
  ↓ installed into
OpenDaylight/Karaf runtime
```
Where we create a class and create a JAR with an OSGi MANIFEST.MF to create a OSGi bundle, and then karaf receives a feature.xml
which describes what our app needs and how it needs it and based on that it is 
installed in the controller's at runtime.

## What are the files we have created so far? 

It is important to note a clear difference between baseline, low level OSGi implementation and higher level patterns defined 
by the OSGi consortium and how opendaylight works. In our case, the code we are implementing in our provider class test, does not 
implement the `BundleActivator` which would be provided alongside an import of `org.osgi:6.0.0` in our `pom.xml` files. Instead
we are using another approach, preferred by Opendaylight which is to use a `Blueprint Container Specification`.

In this case the blueprint files declare the beans that are required durint instantiation of a bundle, as well as the methods
that should be called when destroying or creating said object. This means that Apache Karaf does not randomly receive the JAR
and alongside it the MANIFEST.MF, instead it uses both that and the blueprint to figure out what to isntantiate, and how.


## How are we going to install this application? 

1. First the contents of the feature and the bundle need to copmied into /opt/opendaylight/com ... specific directories as 
if we were registering them in the m2 folder for maven which means that our installation process is going to be a bit more complex 
2. We are going to be needing to add this to the feature install script in Termius and implement a probable two stage system
such that one part of the docker image for the controller compiles the code and the second copies it over into the controller's folders as required.

## What is MD-SAL and why is it used inside the ODL controller? 

> Defined as Model-Driven Service Adaptation layer (MD-SAL), this system is a **message-bus** inspired 
> extensible middleware component that provides **messaging and data storage functionality** based on data and interface
> models defined by application developers.

MD-Sal is constructed around YANG, which is a **modeling language used for both interface and data definitions** which provides 
the runtime for the MD-SAL interfaces. The general idea is that it defines a **common-layer, concepts, data model building blocks 
and messagging patterns** for inter-application communication.

We can operate MD-SAL through two different types, either Bindings or DOM.

### MD-SAL Bindings Model: 

> THe Binding model corresponds to a **Java-friendly API automatically generated from YANG models**. The binding generator
> internally creates **Java Interfaces** for data structures, **Service Interfaces** for RPC and notifications and **Type-safe accessors**
> for reading and writing data in the **Data Store**


