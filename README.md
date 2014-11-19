![Rush](http://www.guitarnoise.com/images/features/rush.jpg)

Rush
====

Rush is Spinnaker's script execution service. It is based on running commands in Docker images. It coordinates execution in docker images via the docker remote api. 

Endpoints
=========

There are 3 endpoints in Rush ( recorded below as http://httpie.org commands ):

Run script
----------

http POST http:/localhost:8080/ops command='groovy -V' image='tomaslin/groovy'

The image is a public docker image found on the docker.io repository. Support for private images and custom repositories will be added soon. 

The command is the command that you wish to execute. 

The result of the run script command is a task id of the current execution

Get Task
--------

http GET http://localhost:8080/tasks/{id}

This returns the status of the task with the desired id.

Get Tasks
---------

http GET http://localhost:8080/tasks

this returns all available tasks.
