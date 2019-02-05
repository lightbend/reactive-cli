### Minikube

on a fresh terminal..

```
minikube delete
minikube start
eval $(minikube docker-env)
kubectl config current-context
sbt
> integrationTest/scripted bootstrap-demo/dns-kubernetes
```

### OpenShift

```
oc new-project reactivelibtest1
export OC_TOKEN=$(oc serviceaccounts get-token default)
echo "$OC_TOKEN" | docker login -u unused --password-stdin docker-registry-default.centralpark.lightbend.com
sbt -Ddeckhand.openshift

> integrationTest/scripted bootstrap-demo/dns-kubernetes
```
