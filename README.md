# BlockChain

What would it take to make a simplistic but functional
crypto currency?

## ToDo

* [X] Generate Transaction message -- send from-addr, amount, to-addr and have node send u back an unsigned txn for that amount
* [X] Use generate_payment endpoint from 'wallet' client
* [X] Add transaction fees to output of coinbase transaction
* [X] Add Peer -- make node track peers that are sent to it
* [X] Distribute blocks -- make node send new blocks to all peers as they are mined
* [X] Distribute new transactions -- make node send new blocks to all peers as they are mined
* [X] Add Validations on Incoming Blocks
* [X] Add Validations on Incoming Transactions
* [ ] Configure Logging setup
* [ ] Use Mount or Component to improve initial state configuration -- ideally also make it easy to run multiple nodes locally
* [ ] Bootstrap new node from peer -- full node should pull all available blocks as needed when connected
* [ ] DB Storage ?? put blocks into SQLite ?? should be much faster
* [ ] DNS Server -- Static node others can connect to on boot to find peers

### DB style conversion

* [X]  55 test/block_chain/block_sync_test.clj
* [X] 244 test/block_chain/integration_test.clj
* [X] 328 test/block_chain/message_handlers_test.clj
* [X] 164 test/block_chain/http_test.clj
* [X] 130 test/block_chain/block_validations_test.clj
* [X]  83 test/block_chain/transaction_validations_test.clj
* [ ]  16 test/block_chain/blocks_test.clj
* [ ]  83 test/block_chain/chain_test.clj
* [ ]  28 test/block_chain/miner_test.clj
* [ ]  81 test/block_chain/target_test.clj
* [ ]  33 test/block_chain/transactions_test.clj

### Payment Generation Workflow

1. User enters address, amount, and fee into wallet
2. Wallet sends these + from-addr to node to generate a payment
3. Wallet receives payment txn from node and signs it
4. Wallet sends add-transaction to node to get added into blockchain

## License

Copyright © 2015 Horace Williams

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

