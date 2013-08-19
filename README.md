jclouds Chef
============

[![Build Status](https://jclouds.ci.cloudbees.com/buildStatus/icon?job=jclouds-chef)](https://jclouds.ci.cloudbees.com/job/jclouds-chef/)

This is the jclouds Chef api. It provides access to the different flavours of the Chef server api:

* [Chef community](http://www.opscode.com/chef/)
* [Enterprise Chef](http://www.opscode.com/enterprise-chef/)

It currently supports versions **0.9** and **0.10** of the standard Chef server apis, and an initial
and very basic (still in progress) implementation of the user and organization api of the Enterprise Chef.

Also provides a set of utility methods to combine Chef features with the jclouds Compute service, allowing
users to customize the bootstrap process and manage the configuration of the deployed nodes.

You will find all documentation in www.jclouds.org

