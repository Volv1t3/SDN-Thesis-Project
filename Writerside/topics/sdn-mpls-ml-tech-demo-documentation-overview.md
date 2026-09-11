# SDN-MPLS-ML Tech Demonstrator Documentation Overview

## General Project Information and Attribution
<deflist collapsible="false" type="full">
<def title="Project Information">
<list type="bullet" columns="2">
<li><b><format color="CornflowerBlue">Project Title:</format></b></li>
<li>Diseño e Implementación de una arquitectura <i>Software Defined Networking</i> (SDN) para clasificación y control adaptativo de tráfico sobre una red MPLS-TE</li>

<li><b><format color="CornflowerBlue">Project Author:</format></b></li>
<li>Santiago Francisco Arellano Jaramillo</li>

<li><b><format color="CornflowerBlue">Project Supervisor:</format></b></li>
<li>Ricardo FLores Moyano</li>

<li><b><format color="CornflowerBlue">Expected Graduation Date:</format></b></li>
<li>December 2026</li>
</list>
</def>
</deflist>

<h2>
Introduction to the project
</h2>
<p>
The <b><code>SDN-MPLS-ML Tech Demonstrator</code></b> project is a networking and machine learning project that combines the notions of <b><code>Software Defined Networking</code></b> along with the traffic classification abilities of machine learning to manage a set of bidirectional MPLS-TE tunnels over an MPLS-TE core network.
</p>
<p>The project consists, from an implementation and technical point of view, of the implementation of three major componentes described beneath</p>
<procedure type="choices" title="Major Implemented Components">
<tabs>
<tab title="MPLS-TE Core Network">
<p>The baseline of this project, the core MPLS-TE network is a four-router network designed over the tested platform of Cisco XRv routers that are provided as images through the Cisco CML application. These images were selected due to their <b>low resource usage and easy virtualization and deployment in Containerlab</b>. </p>
<p>The image version used was <b>Cisco XRv 6.6.3 Full</b> that provided comprehensive support for the entire testing and development procedure for the network topology. It provided all features required out of the box, including the required <i>-TE</i> extensions for most baseline protocols (e.g., OSPF-TE, RSVP-TE and MPLS-TE) with no limitations in terms of configurations until we attempted to implement a complete VRF setup. As reported by Cisco this cannot be done unless we are using a physical Cisco device as the VRF processing requires an ASCI that cannot be easily emulated.</p>
<br/><br/>
<p>The core responsibilities belonging to this layer of the project correspond to:</p>
<ol>
<li><b><format color="CornflowerBlue">Baseline MPLS Label Distribution and Configuration</format></b>: due to limitations of the SDN controller and its interaction with the Cisco XRv routers, the MPLS backbone had to handle two tunnels, one setup in the tunnel headend to tailed and another tailed and to headend direction and report it to the SDN Controller for its management. This meant that the core MPLS network was responsible for distributing the labels corresponding to each router and hop over the network between headend and tailend routers as well as intermediate hops.</li>
<li><b><format color="CornflowerBlue">Traffic Engineering Extensions Configuration</format></b>: this base layer required a per device configuration of all traffic engineering protocols, as well as interfaces and other requirements needed to transmit TE information over OSPF-TE and its distribution between routers to allow for an initial local route to be defined for both tunnels, as well as the state reporting up to the SDN controller.</li>
<li><b><format color="CornflowerBlue">Traffic Engineering Metrics Reporting</format></b>: this layer implemented, alongside all the traffic engineering protocols, corresponding BGP-LS definitions that allowed all routers to connect and exchange network topology information among themselves and with the SDN controller, enabling the SDN controller to extract both network topology information as well as traffic engineering metrics to be used in the path calculation algorithms employed within the SDN controller</li>
<li><b><format color="CornflowerBlue">PCE/PCC Architecture Participation</format></b>: this layer implemented the required configuration at the interface and tunnel level to allow for the <b>previously defined LSPS at the headend and tailend routers</b> to be defined as <b><code>delegated LSPs</code></b> that the SDN controller can see and manage through its PCE components, and use this architecture to push out configuration commands to the PCC devices (headend and tailend routers) to alter the routers and bandwidth requirements the tunnels.</li>
</ol>
</tab>
<tab title="XGBoost Traffic Classification Model">
<p>One crucial component of the implementation corresponded to the XGBoost traffic classification model. This model, implemented in Python using the XGBoost library (which is also used during inference) allowed for the training of a model capable of understanding seven different traffic classes both in the form source to destination, and destination to source packets, allowing for bidirectional classification and policy mapping in the implemented system.</p>
<p>The model was trained on the SDNFlow dataset obtained from the IEEE Dataport publication with help of the corresponding project supervisor, from where the <i>normal</i> flows were selected (those that were not marked as attack flows) and used to train the model based on a <b><code>first-packet-in approach</code></b>, using only four parameters that can be extracted quickly from an incoming packet: <code>ethernet type, IP protocol, source port, destination port</code>.</p>
<p>The reason this approach was taken was that the SDN overlay designed for the project allowed for a notification to be sent to the controller upon the first packet arriving of a new flow, which allowed us to test the whole end to end process much faster. In light of this, the decision was made to use the <b>first-packet-in approach</b> over the configuration of tunnels after the flow has ended as this provides a more sensible approach to the modern SD-WAN approach of dynamic application based routing for optimized resource usage.</p>
<p>In this case, the model is responsible solely for the <b><code>traffic classification</code></b> based on its learned structures and its reporting through the inference APi built around it, which is our next component.</p>
</tab>
<tab title="Python FastAPI Traffic Classification Inference API">
<p>The way the model is used in the project is through an <b>instrumentalized and observable FastAPI instance</b> which is implemented in Python and deployed through a standalone container which contains the model and its configuration and deterministic policies. The idea of this module is to represent a baseline API design, with proper logging, observability and load balancing through the use of classifier pools, structured logging with correlation IDs (for both requests and processing), and prometheus data enpoints for further observability instrumentalization in Grafana.</p>
<p>While this component will be described further in upcoming chapters in this documentation effort, it is important to describe that through it, called from the Java controller side application built to manage the network, the entire system is able to classify the original flow and its response, and use that classification to drive traffic policy definition and finally LSP reconfiguration.</p>
<p>The API is designed with the idea of it supporting at most 5 parallel classification requests, which give it room to grow, and with its configurable 
environment variables, it can support more instances for a larger network, or less for a simpler deployment. These models are preloaded into memory and made available through a load balancing model queue with asynchronous capabilities that performs classification and returns a structured response to the application. Considering that this layer is one above the classifier model, it <b><code>adds to the classification information such as: bandwidth requirements, DSCP and MPLS TC values as well as Setup and Hold Priorities for tunnels </code></b>. This information is later used within the controller side application to define the traffic policy the network requires and configure it. </p>
</tab>
<tab title="SDN Controller Implementation With Controller Side Application">
<p>The SDN controller selected for its features corresponds to the <b><code>Opendaylight Vanadium 0.23.1</code></b>. This controller contained the required <b>NETCONF, Openflow, and BGP-LS</b> configuration requirements that were needed to allow communication between the controller and the MPLS-TE core network. While NETCONF ended up not being used in the final design for the <b>implementation or modification of router settings or tunnel information</b>, it was used initially to directly control flows and to extract the YANG models that we would need in the event that parts of the implementation were not to be available.</p>
<p>The SDN controller selected is sadly not distributed in an up to date image, as the Opendaylight team have not udpated their official Docker Images to present this new version of their controller. Instead, an effort was done to create a complete image which can be used for deployment the controller with the required <b><code>BGP-LS, Openflow, NETCONF, RESTCONF and PCEP components</code></b> enabled, as well as introducing the required controller side application as an enabled feature in the controller.</p>
<p>The SDN controller side application was implemented as an additional module in the underlying Apache Karaf deployment on which Opendaylight is based, that is enabled on startup such that it can learn the topology as soon as the controller itself builds its own information bases, allowing both to synchronize at the same time. Through this model, the controller side application regularly polls information from the controller and its databases, mainly its Traffic Engineering Database, as well as its Openflow Inventory databases to maintain a state of the network and the device within and use said information to detect tunnel directions, tailend and headend routers, as well as other requirements like delegated LSPs that are stored within the controller's databases. With this information, the application can implement the whole business logic of the system, from traffic classification to LSP reconfiguration.</p>
</tab>
<tab title="Grafana Dashboards and Observability">
<p>As a final additional component over the implementation requirements, each of the major systems (the inference API and the controller side application) have been instrumented with Prometheus-style <code>/metrics</code> endpoints which allow for the exporting of gauges, counters and other metrics directly from the components and their use in Grafana Dashboards. In addition to this, all routers have been configured to export their interfaces and interface metrics through SNMP which is digsted through a secondary system such that Grafana can interact with operational, and application relevant metrics.</p>
<p>While this component does not add or take anything from the underlying implementation requirements and the overarching system requirements for the project, it adds a layer of observability which allows for validaiton, metric evaluation, and overall allows the project to achieve its intended goal of being a production-ready system.</p>
<p>Moreover, the controller side application is connected to the <code>RESTCONF</code> API exposed by the entire Opendaylight Controller, such that its state and operational data can be exposed not just through the Grafana endpoint but also through the controller's common operational data retrieval endpoint.</p>
</tab>
</tabs>
</procedure>
<p>All of the major components in this thesis have been implemented following strict engineering guidelines, and have been validated before implementation at every turn, from the Cisco IOS configuration files to the final grafana metrics. This approach of validating before implementation was used due to the uncertainty of the technologies and environments at play. For example, due to the nature of Cisco XRv it was possible that many features were either locked or limited due to the image being a virtualization instead of actual physical hardware, this led to the continuous testing of all configuration mechanisms, which allowed us to define a baseline configuration and to discard ideas such as VRF quickly.</p>
<p>In other cases, this approach helped better map the API requirements of the SDN controller used, as some of the network commands described in the online documentation did not match the expected or required API contracts as described by the actual running instance,  which required validation and testing to determine which commands could be used both by the controller side application and by the operator to configure the system.</p>
<h2>Format and Content of this Documentation</h2>
<p>This documentation effort focuses on describing from a technical perspective the entirety of the implementation and its components, not shying away from technical definitions and concepts while mentioning and describing the implementation details of most components. While the accompanying thesis literature presents the project in a more technical manner, less implementation wise and more about results and future work, this documentation is focused solely on what has been implemented.</p>
<p>Moreover, key tradeoffs and decisions that were identified as the implementation was done will also be discussed and presented when appropriate, and when they are deemed to add context into the decisions taken during the development of this project.</p>
<p>As such, this documentation will be structured as follows:</p>
<deflist type="full">
<def title="Network Topology Information">
<i>This section will describe the entirety of the <b>networking side of the thesis</b>, from the environment setup and requirements, virtualization tools used, complete descriptions of the configurations implemented and topologies defined, as well as any custom images or configurations that have been added alongside to make this system work</i>
</def>
<def title="Model Implementation and Deployment">
<i>This section will describe the entirety of the <b>machine learning side of the thesis</b>, from the dataset used, to the model training and deployment, as well as the inference API and its deployment and configuration. It will use diagrams and class notations to describe the classes and methods that take part in making the whole machine learning system possible.</i>
</def>
<def title="SDN Controller Implementation and Deployment">
<i>This section will present the entirety of the Opendaylight and controller side application implementation and deployment, including how the controller side application works internally, using diagrams to explain data transfers, as well as classes and modules and how they interact internally. In the context of the controller itself, this section will describe the kind of API requests that are required for configuring the corresponding controller, as well as the modules that are required to make this entire topology functional.</i>
</def>
</deflist>