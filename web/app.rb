require "sinatra"
require "socket"
require "json"
require "pry"
require "openssl"
require "base64"

Tilt.register Tilt::ERBTemplate, 'html.erb'

class Wallet
  attr_reader :key_pair

  def initialize(wallet_path = "#{ENV["HOME"]}/.wallet.pem")
    @key_pair = load_or_generate_wallet(wallet_path)
  end

  def load_or_generate_wallet(path)
    if File.exists?(path)
      OpenSSL::PKey.read(File.read(path))
    else
      key_pair = OpenSSL::PKey::RSA.generate(2048)
      File.write(path, key_pair.to_pem)
      key_pair
    end
  end

  def public_key
    key_pair.public_key
  end

  def sign(string)
    Base64.encode64(key_pair.sign(OpenSSL::Digest::SHA256.new, string))
  end

  def public_pem
    public_key.to_pem
  end
end


class ClarkeClient
  attr_reader :host, :port
  def initialize(host = "localhost", port = 8334)
    @host = host
    @port = port
  end

  def send_message(type, payload = {})
    s = TCPSocket.new(host,port)
    message = {"message_type" => type, "payload" => payload}
    s.write(message.to_json + "\n\n")
    resp = JSON.parse(s.read)
    s.close
    resp
  end

  def echo(payload)
    send_message("echo", payload)
  end

  def get_blocks
    send_message("get_blocks")
  end

  def get_block(hash)
    send_message("get_block", hash)
  end

  def get_transaction(hash)
    send_message("get_transaction", hash)
  end

  def get_balance(address)
    send_message("get_balance", address)
  end

  def make_payment(private_pem, receiver_address, amount)
    payload = {private_pem: private_pem, address: receiver_address, amount: amount}
    send_message("make_payment", payload)
  end

  def generate_transaction(from_key, to_key, amount)
    payload = {from_key: from_key, to_key: to_key, amount: amount}
    send_message("generate_transaction", payload)
  end
end

client = ClarkeClient.new
wallet = Wallet.new

def splits(blocks)
  blocks.map { |b| b["header"]["timestamp"].to_i / 1000 }.each_cons(2).map { |a,b| a - b }
end

get "/" do
  @blocks = client.get_blocks["payload"].reverse
  @splits = splits(@blocks)
  erb :"blocks/index"
end

get "/blocks/:hash" do
  @block = client.get_block(params[:hash])["payload"]
  erb :"blocks/show"
end

get "/transactions/:hash" do
  @txn = client.get_transaction(params[:hash])["payload"]
  erb :"transactions/show"
end

post "/balances" do
  @balance_info = client.get_balance(params[:address].gsub("\r\n","\n"))["payload"]
  erb :"balances/create"
end

get "/transactions/pool" do
  # show current txn pool
end

get "/payments/new" do
  @pem = wallet.public_pem
  erb :"payments/new"
end

post "/payments" do
  amount = params[:amount].to_i
  address = params[:address]
  if amount <= 0
    text "Sorry, #{params[:amount]} is not valid."
  else
    unsigned = client.generate_transaction(wallet.public_pem, params[:address], amount)
    # use wallet to sign txn
  end
end

get "/balance" do
  @balance_info = client.get_balance(wallet.public_pem)["payload"]
  erb :"balances/create"
end
