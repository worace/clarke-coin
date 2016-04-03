# BlockChain

What would it take to make a simplistic but functional
crypto currency?

## ToDo

* [ ] Generate Transaction message -- send from-addr, amount, to-addr and have node send u back an unsigned txn for that amount
* [ ] Add Peer -- make node track peers that are sent to it
* [ ] Distribute blocks -- make node send new blocks to all peers as they are mined
* [ ] Bootstrap new node from peer -- full node should pull all available blocks as needed when connected
* [ ] DB Storage -- put blocks into SQLite -- should be much faster
* [ ] DNS Server -- Static node others can connect to on boot to find peers

## License

Copyright © 2015 Horace Williams

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

