# BlockChain

What would it take to make a simplistic but functional
crypto currency?

## ToDo

* [X] Generate Transaction message -- send from-addr, amount, to-addr and have node send u back an unsigned txn for that amount
* [X] Use generate_payment endpoint from 'wallet' client
* [X] Add transaction fees to output of coinbase transaction
* [ ] Add Peer -- make node track peers that are sent to it
* [ ] Distribute blocks -- make node send new blocks to all peers as they are mined
* [ ] Distribute new transactions -- make node send new blocks to all peers as they are mined
* [ ] Bootstrap new node from peer -- full node should pull all available blocks as needed when connected
* [ ] DB Storage ?? put blocks into SQLite ?? should be much faster
* [ ] DNS Server -- Static node others can connect to on boot to find peers

### Payment Generation Workflow

1. User enters address, amount, and fee into wallet
2. Wallet sends these + from-addr to node to generate a payment
3. Wallet receives payment txn from node and signs it
4. Wallet sends add-transaction to node to get added into blockchain

## License

Copyright © 2015 Horace Williams

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

