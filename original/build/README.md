Creates a VM so the original can be built. The results of the original build are used to understand what the expected results are, so these results can, if applicable, be replicated in this project.

The additional VM is used to cache APT artifacts, so the actual build VM can be destroyed and rebuilt in a little less time.

After creating the VMs, SSH into `original-elko-build`:

```shell script
vagrant ssh original-elko-build
vagrant putty original-elko-build
```

Change to directory `Elko/Build` and run one of the make commands documented in `Elko/Build/Makefile`.

Building using other makefiles has not been tested.
