require "sinatra"
require "socket"
require "json"
require "pry"

Tilt.register Tilt::ERBTemplate, 'html.erb'

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
end

client = ClarkeClient.new

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
  erb :"payments/new"
end

post "/payments" do
  binding.pry
end
