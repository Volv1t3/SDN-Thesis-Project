# Network Implementation — Environment Requirements And Setup

This topic documents the baseline host setup used before deploying the MPLS-TE topology. The goal of this page is to prepare an Ubuntu machine with the required Linux tooling, Docker, Containerlab, and the `vrnetlab` project that is later used to wrap Cisco XR virtual router disk images for Containerlab.

<note>
<p>The initial content of this section describes the baseline containerlab and docker installation mechanisms for an <code>Ubuntu 24.04 based host</code>, this is the baseline that was used during the project development as it hosted the entire topology and its support applications. The section on how to create the actual image for running the topology is purposedly limited in the kind of information it provides, mainly on how to find the image. Aside from that, it offers a view on how to transform <code>.qcow2 images to .vmdk images</code> for their use in the topology, and the general commands used through ><code>vrnetlab</code> to produce a VM-based image from these.</p>
</note>

## Scope And Assumptions

<deflist type="full" collapsible="false">
<def title="Host Operating System">
<p>The setup assumes an <b><code>Ubuntu 24.04 or later amd64</code></b> host with administrative access. The commands below use <code>apt</code>, <code>systemctl</code>, Docker Engine, and Linux virtualization checks that are specific to a Linux host environment. </p>
<tip>
<p>This topology can be run inside a Windows-based system provided that virtualization support is active, and that all other requirements are installed, however the <b>nested virtualization level</b> (i.e., containerlab running on top of the WSL2 VM and all hosts running over VM-based docker containers) might cause some issues during deployment. The topology was not tested on any device outside of the Linux-based Ubuntu 24.04 amd64 machine used for deployment.</p>
</tip>
</def>
<def title="Virtual Router Execution Model">
<p>Cisco XR images obtained from Cisco CML are generally virtual router disk images, not native Linux containers. In the Containerlab workflow they are executed through <b><code>vrnetlab</code></b>, which wraps a VM-based network operating system inside a Docker container. While this approach allows the developer to run <b>various cisco and other network device manufacturers images</b> these have to be validated with both <code>vrnetlab and containerlabs</code> support, as not all routers are supported and some <b>due to their resource intensive nature</b> might not be runnable with less than 16 GB of RAM.</p>
<warning>
<p>The environment used to run this had a maximum of 32GB of RAM avaliable, and each router used at most 4GB of RAM at the time, the system was stressed during development and testing up to almost 70% of RAM utilization constantly. Use a small image if you plan on replicating the topology with the same number of routers</p>
</warning>
</def>
<def title="Image Family">
<p>The later topology work assumes a Cisco IOS XR virtual image, normally <b><code>XRv</code></b> or <b><code>XRv9k</code></b>. These platforms are operationally different, so the exact image must be validated before the packaging and deployment steps are documented.</p>
</def>
</deflist>

## Environment Setup — Configuring the local Linux environment for Containerlab

<procedure title="Preparing the Ubuntu Host" id="prepare-ubuntu-host" collapsible="false">
<step>
<p>If the project is to be replicated within a new machine or a VM, the ubuntu system that is used, or the Linux environment used for that matter, has to be updated and requires validating that hardware virtualization is available. This is why it is important to either use a real machine, or perform additional configurations for nested virtualization, as routers raised through <code>vrnetlab</code> will require virtualization support already.</p>

```bash
sudo apt update && sudo apt upgrade -y
```
</step>
<step>
<p>Install the base utilities required for downloading packages, validating virtual disk images, checking virtualization support, and working with Linux bridges. We do not perform any installation of opendaylight, nor <code>Openflow OpenVSwitch</code> or even a JDK for the controller side development, as everything is mediated through containers, whose images are available under the demonstrators docker hub repository.</p>

```bash
sudo apt install -y \
  curl \
  wget \
  git \
  unzip \
  ca-certificates \
  gnupg \
  lsb-release \
  qemu-utils \
  cpu-checker \
  bridge-utils
```
</step>
<step>
<p>Confirm that hardware virtualization is exposed to the Ubuntu host. This is an important requirement as it can make or break the future router deployments through <code>vrtnetlab</code>. Most modern processors and linux variants should detect this automatically. In the case of the testbed for the project </p>

```bash
grep -m1 -oE 'vmx|svm' /proc/cpuinfo
```
<p>This commmand will initially print either <code>vmx</code> which means the system this topology will run on supports Intel VT-x, or <code>svm</code> which means the system exposes AMD-V virtualization. If nothing is outputted then the system does not support hardware virtualization.</p>

```bash
egrep -c '(vmx|svm)' /proc/cpuinfo
```

<p>For this second command, a result of <code>0</code> means the host does not expose any Intel VT-x or AMD-V virtualization cores. VM-based nodes can be unusable or extremely slow in that state. Any other value counts the <b>total amount of cores that the hardware virtualization system reports as usable</b> for these extensions</p>
</step>
<step>
<p>Check KVM availability.</p>

```bash
kvm-ok
```
```console
INFO: /dev/kvm exists
KVM acceleration can be used
```

<p>The desired result is a message indicating that KVM acceleration can be used. Cisco XR virtual nodes are heavy enough that running without KVM acceleration should be treated as a blocker for serious testing.</p>
</step>
</procedure>

## Environment Setup — Installing Docker Engine and configuring non sudo access

Containerlab uses Docker to run topology nodes, including `vrnetlab`-wrapped virtual routers. Install Docker from Docker's official Ubuntu repository rather than relying on the older distribution packages. 

<procedure title="Install Docker Engine" id="install-docker-engine" collapsible="true">
<step>
<p>Remove older Docker packages if they are present.</p>

```bash
sudo apt remove -y docker docker-engine docker.io containerd runc
sudo systemctl stop docker.service docker.socket
sudo rm -f /var/run/docker.sock
sudo rm -f /var/run/docker.pid
```

<p>The previous commands remove any old socket that the docker engine might have installed on the host machine. This was a blocker that was raised during the initial environment setup where an old socket was blocking the newer docker engine from being installed, as an attempt was made to install docker from <code>apt instead of its own repository</code>. If the same mistake is done during installation refer to this step again.</p>
</step>

<step>
<p>Create the keyring directory and install Docker's repository signing key. This has to be done because the installation mechanism adds a <code>docker specific repository later on for pulling the actual engine and installing it</code>, this means that <code>apt in this case</code> needst to have the signing key from docker to validate incoming packages before installing them.</p>

```bash
# Creates the keyrings repository allowing the user to read/write/execute, its group and anonymous users only read/execute access
sudo install -m 0755 -d /etc/apt/keyrings
# Pulling the required key and converting it do the required format for apt
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
# Allowing all users to read the file
sudo chmod a+r /etc/apt/keyrings/docker.gpg
```
</step>
<step>
<p>Add the Docker repository for the current Ubuntu release.</p>

```bash
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```
</step>
<step>
<p>Install Docker Engine, the Docker CLI, containerd, Buildx, and the Compose plugin.</p>

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```
</step>
<step>
<p>Enable and start the Docker service.</p>

```bash
sudo systemctl enable docker
sudo systemctl start docker
```
</step>
<step>
<p>Optionally allow the current user to run Docker without prefixing every command with <code>sudo</code>.</p>

```bash
sudo usermod -aG docker "$USER"
newgrp docker
```
</step>
<step>
<p>Verify that Docker can run containers.</p>

```bash
docker run hello-world
```
</step>
</procedure>

## Environment Setup — Containerlab Installation

Containerlab provides the topology orchestration layer used by the network implementation. It creates the lab namespace, starts the topology containers, wires links, and exposes commands for inspecting or destroying the lab. It is specially improtant to install this from the official sources, as the tooling that comes along with it is crucial for managing bandwidth, deployments and properly configuring links.

<procedure title="Install Containerlab" id="install-containerlab" collapsible="true">
<step>
<p>Install Containerlab using the official installation script.</p>

```bash
bash -c "$(curl -sL https://get.containerlab.dev)"
```
</step>
<step>
<p>Verify that the binary is available.</p>

```bash
containerlab version
```
</step>
<step>
<p>Confirm that Containerlab can access Docker.</p>

```bash
docker version
containerlab version
```
</step>
</procedure>

## Environment Setup — vrnetlab Baseline Setup

`vrnetlab` is the bridge between Containerlab and VM-based network operating systems. It contains build recipes for different vendors and platforms, including Cisco XR variants when supported by the local `vrnetlab` version.

<procedure title="Install vrnetlab Project Files" id="install-vrnetlab-project" collapsible="false">
<step>
<p>Choose a working directory for lab build sources. The example below keeps third-party tooling under <path>~/network-lab-tools</path>. While the folder will not be used at any other point of the project, it can be useful to organize the build dependencies for the Cisco XRv or other images to be used in topologies or this project.</p>

```bash
mkdir -p ~/network-lab-tools
cd ~/network-lab-tools
```
</step>
<step>
<p>Clone the <code>vrnetlab</code> repository. The repository we are cloning is a forked version of the original <code>vrnetlab</code> project which is maintained for its used with <code>containerlab</code>. The main difference between these two is the way they handled data path and link communication between nodes. </p>
<p>For the baseline <code>vrnetlab</code> image, communication is done through a separate VM which would add another layer of orchestration into an already complex project. For this reason containerlab uses this different image which adds a less vm dependand communication mechanism by stitching together interfaces and data path links as declared in containerlab to containers. The repository goes into much more depth than what we are going to go in here, but this image can explain the interconnection mechanism</p>
<img src="https://camo.githubusercontent.com/459fa7f487709ae2f9be41dbccdbc854e7c09ef650064880a860255d7f3c08d9/68747470733a2f2f6769746c61622e636f6d2f72646f64696e2f706963732f2d2f77696b69732f75706c6f6164732f34643331633036653632353865373065646338383762313765306537353865302f696d6167652e706e67"></img>

```bash
git clone https://github.com/hellt/vrnetlab.git
cd vrnetlab
```
</step>
<step>
<p>Inspect the available platform directories.</p>

```bash
find . -maxdepth 2 -type d | sort
```
</step>
<step>
<p>Look specifically for Cisco IOS XR-related targets. In our case we can either keep using a terminal or navigate the folder until we find the structure <code>./cisco/xrv</code>.</p>

```bash
find . -maxdepth 2 -type d | grep -Ei 'xrv|xrv9k|iosxr|ios-xr|cisco' | sort
```
</step>
<step>
<p>Record which target directory appears to match the intended Cisco image family. For this project, the expected candidates are usually one of the XRv or XRv9k targets, but the exact directory must be validated against the actual image used later.</p>
</step>
</procedure>

## Image Configuration — creating the required image with vrnetlab

<p>Now that the environment has been configured, the baseline image used for the routers in the MPLS-TE core of the network have to be produced such that they can be effectively reused in every containerlab topology onwards, and in the current topology too. In order to do so we are going to require two things</p>
<ul>
<li><b><format color="CornflowerBlue">vrnetlab installed</format></b>: we did this in the previous steps, it should be available and the folder for the Cisco XRv router should be available.</li>
<li><b><format color="CornflowerBlue">Cisco XRv <code>qcow2</code> or <code>vmdk</code> image</format></b>: this is the trickies part of this setup, and in an effort to not cause any kind of problems due to this thesis, the image link will not be provided. This guide will assume the reader has an image with either extension.</li>
</ul>
<p>With these two elements we can begin working on building the image.</p>
<procedure type="steps" title="Building an Image of a Cisco XRv disk image">
<step>
First, copy the acquired image into the corresponding <code>vrnetlab</code> folder. This folder will contain the make file required for producing the internal image that will run through Qemu, and the Dockerfile recipe to create the image.

```bash
cp /path/to/your/cisco_xrv.qcow2 ~/network-lab-tools/vrnetlab/cisco/xrv
# If the image found has another extension then
cp /path/to/your/cisco_xrv.vmdk ~/network-lab-tools/vrnetlab/cisco/xrv
```

<p>At this step, it does not matter what kind of image was found insofar as it belongs to either of the extensions presented above. This guide assumes the image found is a <code>qcow2</code> version.</p>
</step>
<step>
<p>One of the key constraints that the <code>vrtnetlab</code> images require is that the filename distinguishes the exact version of the Network Operating System being used. The exact regex expression used is the following:</p>

```regex
VERSION=$(shell echo $(IMAGE) | sed -e 's/.\+[^0-9]\([0-9]\+\.[0-9]\+\.[0-9]\+\(\.[0-9A-Z]\+\)\?\)\([^0-9].*\|$$\)/\1/')
```
<p>In this case, the recommended name to be used is xrv-k9-&lt; version information &gt; as it will match the regex check. For this reason, most names should be renamed to this, independently of the extension of the image.</p>
</step>
<step>
<p>Once the image has been acquired, comes the conversion part. <code>vrnetlab</code> works well and creates the image based on the <code>.vmdk</code> extension, and it does not support the <code>.qcow2</code> extension some images might come in. As such, we execute this command to transform an image with <code>.qcow2 to .vmdk</code></p>

```bash
qemu-img convert -f qcow2 -O vmdk iosxrv-k9-demo-6.6.3.qcow2 iosxrv-k9-demo-6.6.3.vmdk
```
</step>
<step>
<p>With the image converted, we can make use of the attached make file to execute the docker build context associated with the <code>vrnetlab cisco xrv images</code>.</p>

```bash
sudo make docker-build IMAGE=iosxrv-k9-demo-6.6.3.vmdk
```

<warning>
The commands executed here make the image belong to the <code>sudo docker engine</code> which is independent of the normal user docker engine image repository. In this case, only topologies deployed with <code>sudo</code> will see this image as available.
</warning>
</step>
</procedure>
