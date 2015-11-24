# Iteration 0 - Creating Wallets, Creating Transactions, and Signing/Serializing Transactions

## Wallets

Many cryptocurrencies include a component called a "Wallet" which
provides a handful of tools to let users interact with their money.
In this iteration, the main functions we'll be interested in are:

1. Generating public/private keys
2. Storing and retrieving keypairs from the filesystem
3. Producing and signing transactions
4. Serializing transactions so that they can be distributed
over the network

### 1 - Generating and Storing a Public/Private Key Pair

Fortunately this process is fairly straightforward for us since
there are plenty of tools out there that implement the underlying
cryptography for us.

We'll follow Bitcoin's lead and use a Public/Private Cryptographic
algorith called the "Elliptic Curve Digital Signature Algorithm" or
ECDSA. There's actually some pretty interesting math behind this which
you can [read up on](https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm),
but what we need to know is that it's becoming a go-to algorithm for
public-private key cryptography (as a replacement for RSA which had been the dominant
standard since the 1970's).

To start with a basic wallet implementation, write a program which, when run, will
look for a special hidden file on the user's computer (perhaps `~/.clarke` ?).
If the file exists, the program will expect a valid wallet to exist there in
the form of a public/private key pair. It should read this into memory and print
the public key (in general, we want to avoid printing a private key).

If the program doesn't find this file, it should create it by first generating
a keypair and then writing them to the wallet file.

### Generating a Keypair

To generate keys, we'll want to use a library that implements ECDSA.
In Ruby, [this gem](https://github.com/DavidEGrayson/ruby_ecdsa) is a great
option (Ruby's OpenSSL library does include this, but the API is not very well documented).

Other languages will almost certainly have a convenient library providing
this algorithm as well.

__A Note on Curves:__ One trick with ECDSA is that we need to agree on which
"curve" we're going to use for exchanging keys. Bitcoin uses a curve
called "secp256k1", and we'll follow this convention as well. When
you're generating keypairs with ECDSA make sure you specify this curve.

### 2 - Generating a Transaction

A "transaction" represents the standard unit of exchange within our currency.
It's how coins get transferred from one wallet address (public key) to
another. As we'll see, we use our private key to _sign_ this transfer,
mathematically proving that we are authorized to transfer this money.

Fundamental to creating a Transaction is the idea of individual chunks
of currency referred to as "Transaction Outputs." When we "spend" coins
in Bitcoin, we are actually transferring Transaction Outputs. This
transfer will in turn generate new Transaction Outputs that could
later be spent by the new owner as an input to a different transaction.

Thus we can think of a transaction as a collection of inputs on
one side and outputs on the other.

Where do the original outputs come from? Ultimately we'll be generating
them through the mining process. However for now (since we're starting
at the "bottom" with just wallets and transactions), we'll want
to figure out some way to mock that part out.

#### Transaction Contents

* Consists of transaction Input(s) and Output(s)
* Inputs represent allotments of currency that were assigned to
a given address using that address as a public key
* As the owner of the addres, you can use the associated
key to "unlock" the specified allotment of coins and thus send these
to another address
* Generally there will be 1 output for the amount you are trying to
send, and frequently an additional 1 output to send "change" back
to the spending address

### 3 - Signing a Transaction

* Can generate a new transaction to send money to a specified address
by signing the *from* address with the associated private key
* *optional:* using fresh keypairs for new transactions
* Serializing keys: When sending over the network, we'll
use simple hexadecimal encoding

### Transactions

### Verifying transactions

As transactions get propagated to the network, clients will need to verify
several things about the transaction:

1. All transaction inputs must have a valid signature proving
that the sender has authority to use those inputs
2. All outputs must be assigned to valid addresses
3. All inputs must still be available for spending

### Transaction Outputs

The system is designed around transferring currency in discrete chunks
or allotments, called "outputs". To spend currency, a user really spends
"outputs" of previous transactions by transferring them to a new address.

We will sometimes use the term "input" to refer to the outputs that are
going into a transaction, but remember that every transaction input is ultimately
just an output of a previous transaction.

#### Change

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
