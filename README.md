# Reactive Missile Defend

[Slides from Krakow Scala User Group](https://rawgit.com/otrebski/reactive-missile-defend/master/doc/reactivemissledefend.html#slide-0)
# How cluster look like #
Cluster should have following nodes:

* Shared journal node (Shared level DB) (sbt sj)
* Swing UI with Game engine (sbt ui)
* Command centers to run defence tower logic (choosing target and calculating interception vector) (sbt cc)
* Optional CLI UI (sbt cliui)

# How to run only local nodes (using SBT): #
First start shared journal. It will be also seed node. This node is Single Point Of Failure, so do not kill it. First export configuration:

* ```export SEED_NODE=akka.tcp://defend@127.0.0.1:3000```
* ```export HOST=127.0.0.1```
* ```export PORT=3000```
* ```sbt sj```

Run Swing UI. This node is also game engine. Do not kill it. In another terminal run

* ```export SEED_NODE=akka.tcp://defend@127.0.0.1:3000```
* ```export HOST=127.0.0.1```
* ```export PORT=2500```
* ```sbt ui```

Run Command Centers nodes (as many as you want). Every instance need to have different PORT

* ```export SEED_NODE=akka.tcp://defend@127.0.0.1:3000```
* ```export HOST=127.0.0.1```
* ```export PORT=2551```
* ```sbt cc```

In UI choose click "Start" to begin invasion. When attack is running you can kill or restart Command centers node and watch what will happened.

Enjoy

# How to run using a few computers

First build runnable application by ```sbt clean universal:packageBin```. Result of build will be zip file ```missiledefend-x.y.z.zip``` in ```target/universal/```
Use ```./missiledefend-x.y.z/bin/missiledefend``` to run application (with arg ```ui``` for UI, ```sj``` for shared journal, ```cc``` for command center node.
You have to also export following env variables:

 * ```HOST``` - IP address used for communication
 * ```PORT``` - Port number
 * ```SEED_NODE``` - Akka seed node for example: ```akka.tcp://defend@192.168.0.3:3000```

Start nodes:

 * ```missiledefend sj``` - shared journal (can be run with UI on the same machine)
 * ```missiledefend ui``` - UI
 * ```missiledefend cc``` - Command Center to host defence towers
 * ```missiledefend cc``` - Another Command Center on another machine

# Scenarios to run

## Power failure, JVM/System crush
* Start cluster
* Start game
* Kill one of "Command Center" JVM
* Wait for towers to be migrated and node to be disconnected (change color to red)
* Restart JVM
* Wait for node to be connected again


## Network failure
* Start cluster with one Command Center node on remote machine
* Disconnect network on remote machine
* Wait for towers to be migrated and node to be disconnected (change color to red)
* Connect network again, node will not be connected again.
* Restart JVM, node will be connected again

## Persistence crash
* Start cluster
* Start game
* Kill node with persistence (./missiledefend sj)
* Kill one of "Command Center" JVM
* Watch problems of migrating actors (Sharding depends on persistence)

## Drop some traffic
* Start cluster with one Command Center node on remote machine
* Use "iptable" to drop 1%, 2%, 5%, 10% of traffic using iptables command:
   ``` 
   iptables -A INPUT -m statistic --mode random --probability 0.2 -j DROP 
   iptables -A OUTPUT -m statistic --mode random --probability 0.2 -j DROP 
   ```
* Watch how cluster behaves
   
## Graceful leaving cluster
* Start cluster
* Start game
* Right click on one of command centers, select "Cluster leave"
* Watch migration time
* Restart node
* Run "Cluster leave again"
   
   
## Detecting how many messages where lost
* Start cluster with 2 "Command Center" nodes
* Start game
* Click on one of the towers, it's name should be displayed.
* Kill "Command Center" on which selected tower is running
* Wait for towers to be migrated and node.
* Kill cluster
* Grep all logs with tower name. Messages to towers are indexed, so it is possible to check how many messages where lost. Logs should looks like this:
  ```
  Received situation 425
  Received situation 426
  Received situation 427
  Starting T400-214 on akka.tcp://defend@127.0.0.1:3008
  ...
  ...
  Recovery completed for T400-214 on akka.tcp://defend@127.0.0.1:3008
  Received situation XXX
  ```
  
  
  