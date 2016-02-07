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
end

client = ClarkeClient.new

get "/" do
  @blocks = client.get_blocks["payload"]
  erb :"blocks/index"
end

get "/blocks/:hash" do
  @block = client.get_block(params[:hash])["payload"]
  erb :"blocks/show"
end

get "/transactions/:hash" do
  # list json of that transactions
end

get "/transactions/pool" do
  # show current txn pool
end

get "/balances/new" do
  # show balance check form
end

post "/balances" do
  # read address from params and show that address's balance
end
