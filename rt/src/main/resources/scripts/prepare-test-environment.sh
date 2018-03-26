#!/bin/sh

#install kubernetes and openshift CLI tools
kube_version=$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)
curl -LO https://storage.googleapis.com/kubernetes-release/release/${kube_version}/bin/linux/amd64/kubectl && \
    chmod +x kubectl && sudo mv kubectl /usr/local/bin/
echo "Installed kubectl CLI tool"

oc_tool_version="openshift-origin-client-tools-${OC_VERSION}-${COMMIT_ID}-linux-64bit"
curl -LO https://github.com/openshift/origin/releases/download/${OC_VERSION}/${oc_tool_version}.tar.gz && \
    tar -xvzf ${oc_tool_version}.tar.gz && chmod +x $PWD/${oc_tool_version}/oc && sudo mv $PWD/${oc_tool_version}/oc /usr/local/bin/ && \
    rm -rf ${oc_tool_version}.tar.gz
echo "Installed OC CLI tool"

#add insecure docker registry
tmp=`mktemp`
echo 'DOCKER_OPTS="$DOCKER_OPTS --insecure-registry 172.30.0.0/16"' > ${tmp}
sudo mv ${tmp} /etc/default/docker
sudo mount --make-shared /
sudo service docker restart
echo "Configured Docker daemon with insecure-registry"

#make OpenShift up & running
oc cluster up --version=${OC_VERSION}
sleep 10
oc login -u developer -p developer
echo "Configured OpenShift cluster : ${OC_VERSION}"
