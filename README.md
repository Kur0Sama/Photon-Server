![](https://img.shields.io/badge/next%20version-0.5.0-yellow.svg)
![](https://img.shields.io/badge/progress-5%25-red.svg)
[![](https://img.shields.io/badge/discord-join%20chat!-7289DA.svg)](https://discord.gg/vWYembz)

From-scratch, extensible and multithreaded minecraft server.

## License
- api: LGPL
- impl and everything else: AGPL

## How to build
Photon uses [gradle](http://gradle.org) to manage its dependencies. Building the project is very easy:

1. Install the Java 8 SDK (aka JDK 8) if you don't already have it.
2. Put the [Photon Protocol library](https://github.com/mcphoton/Photon-ProtocolLib) in the same directory as the Photon Server project folder. 
3. In the Photon Server project's directory, run this command: `./gradlew build`

**Note:** The Photon-API repository is now useless and won't be updated anymore. The API is now
part of this repository.

## How to contribute
You can fork this project and send me pull requests :)
* To contribute to the Photon implementation, you must first understand the Photon API. Please read [the API wiki](https://github.com/mcphoton/Photon-API/wiki) to know about the project's guidelines and architecture.
* Please also read *the server architecture* (coming soon) to understand how the server is organized.
* Good java skills are required.

## Branches
The *develop* branch contains the latest **in development** version of the Photon server. It's probably unstable and may not work at all. More stable releases can be found in the *master* branch.

## Current status
The Photon server **isn't ready** yet. As of release "0.4.0" you can connect to it, but you can't do anything in the world (well, actually there's no real world: the map is completely empty).

The progress of the project can be found [here](https://github.com/mcphoton/Photon-Server/projects/1).