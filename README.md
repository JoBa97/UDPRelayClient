# UDPRelayClient
Create a peer to peer UDP connection using UDP hole punching and tunnel TCP and UDP traffic over this connection.

## Usage
 - `java -jar UDPRelayClient.jar [arguments]`
 - `-d|--destination`  IP/hostname of the peer on the other end.
 - `-r|--relay` IP/hostname of the server used for the hole punching process.
 - `-p|--port` serverside port used for hole punching. Default: 52125
 - `-t|--token` a unique token used by you and your peer. It is required for the server to know which clients want to get connected.
 - `--rules` Port forwarding rules, comma seperated. Rules are not required but the programm is useless without port forwarding. Rules can be added or removed later at runtime.
 
## Rules & commands
 - `show` shows a list of all active port forwarding rules
 - `exit` exits the programm
### Rules
Every rule starts with one of the three following symbols
- `+` to create a new rule with a known destination port.
 - Syntax: `+<t(cp)|u(dp)><channel>-<[host:]port>`
- `-` to remove a rule.
 - Syntax: `-<channel>`
- `?` to create a rule with an unknown destination port.
 - Syntax: `?<t(cp)|u(dp)><channel>-<[host:]port>`

Example:
Alice and Bob want to play Minecraft together, but they are both behind a NAT with no port forwarding.
They both start the client, connect and now want to set up the port forwarding, so that Alice can host the game, and Bob can connect to Alice's server.
Alice use the following rule: `+t1-25565`. This sends the traffic received at channel 1 of the UDP connection to the local port 25565 on Alice's computer.
Bob uses this rule: `?t1-25566` since he doesn't know which port his game will bind to, he can only controll which port it sends it's data to.
Now that everything has been set up, Bob can connect to Alice's server on `localhost:25566`
