# Reactive Missile Defend

Reactive Missile Defend is an application to demonstrate how Akka Cluster ([Sharding](http://doc.akka.io/docs/akka/snapshot/scala/cluster-sharding.html) and [Singleton](http://doc.akka.io/docs/akka/snapshot/scala/cluster-singleton.html)) works in stressful situation like network or power problem.  The application is simulation of missile defence system. To intercept enemy missiles/bombs we are using missile towers. Every tower is seperate instance of Akka actor. These actors are distributed on many nodes. You can kill one of nodes and watch how actors are migrated.

Game looks like this:

![view](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/doc/images/screen_general.png)

## Presentation
Recording from talk is available [Scala by the bay youtube channel](https://www.youtube.com/watch?v=g7LHqcMg6MI)

## Defence

Our defence missile towers (one actor per each) and command centers (cluster nodes on which tower actors are run).

Towers can be in state:

![Ready](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/tower_ready.png) Ready to fire missile

![Reloading](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/tower_reloading.png) Realoding after firing missile

![Offline](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/tower_offline.png) Offline - UI does not receive message from this tower for more than 500ms

![Infected](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/poop-smiley.png) Infected by enemy virus, cant fire missile.

Command centers cluster nodes with started [Sharding](http://doc.akka.io/docs/akka/snapshot/scala/cluster-sharding.html) for [TowerActor](https://github.com/otrebski/reactive-missile-defend/blob/master/src/main/scala/defend/shard/TowerActor.scala). Command centers are displayed in form:

``` IP => number of tower actors running ```

In case of running on local host:

``` PORT => number of tower actors running ```

Under the tower is an icon. This icon represent one of command centers. It visualize on which node tower actor is running. Move mouse cursor on command center and towers will be highlighted.

![view](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/doc/images/screen_selected_towers.png)

And can be in color:

 * Green - node is up and running
 * Orange - Akka cluster detects that node is [unreachable](http://doc.akka.io/docs/akka/snapshot/scala/cluster-usage.html#Subscribe_to_Cluster_Events).
 * Red - Akka cluster removes node from cluster or node [has left cluster](http://doc.akka.io/docs/akka/snapshot/scala/cluster-usage.html#Leaving)

## Alien weapons

![Missile](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/alien_rocket.png) Alien missile - fast, small radius and low damage

![Bomb](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/bomb.png) Bomb - slow small radius and medium damage

![Nuke](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/radioactivity.png) Nuke - big damage and radius

![Virus](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/game.png) Virus - Infect defence towers, move actor to Infected state for a few second. In this state tower can't shoot (actor is ingoring messages)

[Slides from Krakow Scala User Group](https://rawgit.com/otrebski/reactive-missile-defend/master/doc/reactivemissledefend.html#slide-0)
# How cluster looks like #
Cluster should have following nodes:

* Swing UI with Game engine (sbt ui)
* Command centers to run defence tower logic (choosing target and calculating interception vector) (sbt cc)
* Optional CLI UI (sbt cliui)

# Requirements #

## Cassandra as persistence ##

This project uses Cassandra 2 or 3 as a persistence. You can run cassandra using [Docker](https://www.docker.com/) or connect to existing Cassandra cluster. Details of Cassandra docker image can be found [here](https://hub.docker.com/_/cassandra/). Two keyspaces will be created:
* rmd_journal - for journal
* rmd_snapshot - for snapshots
These keyspaces can be deleted between running game. 

### Using Cassandra from docker ###
```
docker pull cassandra:2.2

docker run -d -p 9042:9042 --name cass cassandra:2.2
```
If you are Windows or OSX user you have to add port forwarding for port 9042.

### Specify Cassandra host using environment variables
By default Cassandra host is 127.0.0.1
```
export=CASSANDRA_HOST=myIp
```

# How to run only local nodes (using SBT): #
First start Cassandra which will be used as a persitence for Actors. You can run Cassadnra using docker or downloaded binaries.

Run Swing UI. This node is also game engine. Do not kill it. In terminal run

* ```export SEED_NODE=akka.tcp://defend@127.0.0.1:3000```
* ```export SEED_NODE2=akka.tcp://defend@127.0.0.1:4000```
* ```export HOST=127.0.0.1```
* ```export PORT=3000```
* ```export CASSANDRA_HOST=myhost```
* ```sbt ui```

Run Command Line UI  node (at least one). Every instance need to have different PORT. It can be used to observe Cluster Singleton migration / failover.

* ```export SEED_NODE=akka.tcp://defend@127.0.0.1:3000```
* ```export SEED_NODE2=akka.tcp://defend@127.0.0.1:4000```
* ```export HOST=127.0.0.1```
* ```export PORT=4000```
* ```export CASSANDRA_HOST=myhost```
* ```sbt cliui```

Run Command Centers nodes (as many as you want, at least 1). Every instance need to have different PORT

* ```export SEED_NODE=akka.tcp://defend@127.0.0.1:3000```
* ```export SEED_NODE2=akka.tcp://defend@127.0.0.1:4000```
* ```export HOST=127.0.0.1```
* ```export PORT=2501```
* ```export CASSANDRA_HOST=myhost```
* ```sbt cc```

In UI choose click "Start" to begin invasion. When attack is running you can kill or restart Command centers node and watch what will happened.

Enjoy

# How to run using a few computers

First build runnable application by ```sbt clean universal:packageBin```. Result of build will be zip file ```missiledefend-x.y.z.zip``` in ```target/universal/```
Use ```./missiledefend-x.y.z/bin/missiledefend``` to run application (with arg ```ui``` for UI, ```cc``` for command center node.
You have to also export following env variables:

 * ```HOST``` - IP address used for communication
 * ```PORT``` - Port number
 * ```SEED_NODE``` - Akka seed node for example: ```akka.tcp://defend@192.168.0.3:3000```
 * ```SEED_NODE2``` - Second Akka seed node for example: ```akka.tcp://defend@192.168.0.3:4000```
 * ```CASSANDRA_HOST``` - Contact point for Cassandra, default value is 127.0.0.1

Start nodes:

 * ```missiledefend ui``` - UI - This node will host StatusKeeper actor (Cluster Singleton)
 * ```missiledefend cliui``` - Command line UI - This node will host StatusKeeper actor (Cluster Singleton)
 * ```missiledefend cc``` - Command Center to host defence towers
 * ```missiledefend cc``` - Another Command Center on another machine

# Scenarios to run

## Power failure, JVM/System crash
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
* Kill Cassandra 
* Watch problems of Tower Actors (Sharding depends on persistence)
* Restart Cassandra
* Situation should go back to normal after while (if outage was short, less than ~30s)

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
* On Ui under the tower will be displayed icon ![Lost messages](https://raw.githubusercontent.com/otrebski/reactive-missile-defend/master/src/main/resources/icons/mail--exclamation.png). Number under the icon shows how many messages are lost. In case the messages were delivered in wrong order, system also detect message lost.
  

  
  # License
   Code is available under Apache Commons 2.0 License
   Icons are available under a Creative Commons Attribution 3.0 License. Most icons are downloaded from http://p.yusukekamiyamane.com/
  
