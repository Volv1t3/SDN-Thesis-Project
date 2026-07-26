# Environment Requirements per System Required

## System Requirements -- Baseline 



## Open V Switch Installation -- Baseline

### REQ-01: `libcap-ng` on Ubuntu 24

```zsh
sudo apt-get update
sudo apt-get install build-essential autoconf automake libtool pkg-config

git clone https://github.com/stevegrubb/libcap-ng.git
cd libcap-ng 
./autogen.sh
./configure
make
sudo make install
```

### REQ-02: `OpenSSL` 

```zsh
sudo apt install openssl
```

### REQ-03: `Unbound Library`

```zsh
sudo apt install unbound
```

### Final Installation

```zsh 
sudo apt install openvswitch-common 
sudo apt install openvswitch-switch-dpdk 
sudo apt install openvswitch-doc 
sudo apt install openvswitch-switch
```

## Containerlab Installation -- Baseline 

