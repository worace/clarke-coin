# BlockChain

What would it take to make a simplistic but functional
crypto currency?

## Wallets

* Generates private/public key/address pairs
* Stores key pairs on filesystem so that they can be re-used
on subsequent sessions
* Can generate a new transaction to send money to a specified address
by signing the *from* address with the associated private key
* *optional:* using fresh keypairs for new transactions
* Serializing keys: When sending over the network, we'll
use [Base58 Check Encoding](https://en.bitcoin.it/wiki/Base58Check_encoding)

## Transactions

* Signed authorization to transfer coins from one address to
another
* Consists of transaction Input(s) and Output(s)
* Inputs represent allotments of currency that were assigned to
a given address using that address as a public key
* As the owner of the addres, you can use the associated
key to "unlock" the specified allotment of coins and thus send these
to another address
* Generally there will be 1 output for the amount you are trying to
send, and frequently an additional 1 output to send "change" back
to the spending address

### Verifying transactions

As transactions get propagated to the network, clients will need to verify
several things about the transaction:

1. All transaction inputs must have a valid signature proving
that the sender has authority to use those inputs
2. All outputs must be assigned to valid addresses
3. All inputs must still be available for spending

## Transaction Outputs

The system is designed around transferring currency in discrete chunks
or allotments, called "outputs". To spend currency, a user really spends
"outputs" of previous transactions by transferring them to a new address.

We will sometimes use the term "input" to refer to the outputs that are
going into a transaction, but remember that every transaction input is ultimately
just an output of a previous transaction.

### Change

When spending an output, it must be consumed in its entirety. However often
we will want to transfer an amount that doesn't exactly match the inputs
we are feeding in. In these cases we will need to return "change" back to
ourselves.

Since all currency must be transferred in the form of discrete outputs, our
change will simply form another output of the transaction. Thus a transaction
will often take the form of 1 input -> 2 outputs, where 1 of the outputs
is going to the other party's address and the other output goes back to
our own address in the form of "change"

It's important that change appears as another output within the same
transaction (as opposed to a separate transaction). This allows
a single input to be split into multiple outputs at once, and also
guarantees that our change and transfer outputs can't be separated from
one another.

## Blocks

Blocks form the "entries" into the shared, public ledger that our
currency depends on. Or perhaps more accurately, blocks form "pages"
in the ledger, with transactions making up the individual entries.

This is because a block is basically a collection of transactions
that get committed to the ledger (aka the blockchain). This process
is a bit involved but it provides the fundamental assurances on which
the whole system is based, so it's very important.

### Block Ingredients

### Block Headers & Hashing

* Contain multiple transactions

## Node Communication

## Account Tracking

* Monitor blockchain for transactions relevant to your wallet

### Unknowns (Numerous and Deep)

__Q:__ How do network nodes communicate?

* Needs to be peer-to-peer
* Working only LAN keeps things simple and works for our
intended educational purpose

__Q:__ How do nodes discover one another?

* To keep it truly decentralized it needs to do without
any bootstrap server
* UDP Multicast seems promising -- on connecting to network
(and certain other events), peers can broadcast to network to
find other nodes

__Q:__ What does each block contain

* list of transactions
* Signature of generating node
* Hash of previous block
* [More Info](https://www.igvita.com/2014/05/05/minimum-viable-block-chain/)
* timestamp?

__Q:__ What does bootstrapping a client/node look like?

* Needs public/private keypair?
* Needs to pull existing blockchain from multiple peers

__Q:__ How current balances get calculated?

* Replay ledger from beginning to now; miners
are identified by their...(public key? some signature?)

__Q:__ How does a transaction get signed?

__Q:__ How does a block get signed?

__Q:__ How do nodes distribute log updates?

__Q:__ How do nodes agree on current target?

* Generate next target as function of frequency of
recent blocks
* Desired block frequency is coded into clients?
* is it ok for this to be static?

__Q:__ Is RSA ok for signing?

* ECDSA needed?

__Q:__ What would be required to take it out of LAN?

* TCP punching?
* What is the bootstrapping/discovery mechanism?

## License

Copyright © 2015 Horace Williams

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
