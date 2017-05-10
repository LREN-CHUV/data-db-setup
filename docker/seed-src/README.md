# Seed sources

This project is used to force Maven to download its dependencies before executing the real build.

We use it to build a cache of Maven dependencies inside the multi-stage build, thus avoiding downloading the internet each time a project file has changed.
