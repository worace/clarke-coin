require 'sshkit'
require 'sshkit/dsl'
include SSHKit::DSL

DEPLOYED_NODES = ["159.203.206.61", "159.203.206.63", "159.203.206.49"].map do |ip|
  SSHKit::Host.new("root@#{ip}")
end

def docker_command
  if RUBY_PLATFORM.include?("darwin")
    "docker"
  else
    "sudo docker"
  end
end

desc "Shutdown docker containers on miner nodes"
task :stop do
  on DEPLOYED_NODES do |host|
    puts "Stopping miner on node #{host}"
    execute "docker ps -ql | xargs docker stop"
  end
end

desc "Start docker containers on miner nodes"
task :start do
  on DEPLOYED_NODES do |host|
    puts "Starting miner on node #{host}"
    execute "docker run -d -v /var/lib/clarke-coin:/var/lib/clarke-coin -p 3000-3000:3000-3000 worace/clarke-coin:latest"
  end
end

desc "Wipe wallets on miner nodes"
task :wipe_wallets do
  on DEPLOYED_NODES do |host|
    puts "Stopping miner on node #{host}"
    execute "rm -rf /var/lib/clarke-coin/wallets"
  end
end

desc "Delete DBs on miner nodes"
task :wipe_dbs do
  on DEPLOYED_NODES do |host|
    puts "Wiping DB on node #{host}"
    execute "rm -rf /var/lib/clarke-coin/db"
  end
end

desc "Rebuild the docker image and push it to dockerhub"
task :docker_push do
  puts "Building and pushing docker image..."
  sh "#{docker_command} build -t worace/clarke-coin ."
  sh "#{docker_command} push worace/clarke-coin:latest"
end

desc "Pull the latest docker image to each server and run it"
task :deploy => [:docker_push] do
  on DEPLOYED_NODES do |host|
    puts "Deploying to node #{host}"
    execute "docker pull worace/clarke-coin:latest"
    execute "docker ps -ql | xargs docker stop"
    execute "docker run -d -v /var/lib/clarke-coin:/var/lib/clarke-coin -p 3000-3000:3000-3000 worace/clarke-coin:latest"
  end
end
