require 'sshkit'
require 'sshkit/dsl'
include SSHKit::DSL

DEPLOYED_NODES = ["159.203.206.61", "159.203.206.63", "159.203.206.49"].map do |ip|
  SSHKit::Host.new("root@#{ip}")
end

def stop_container
  execute "docker ps -ql | xargs docker stop"
end

def pull_image
  execute "docker pull worace/clarke-coin:latest"
end

def start_container
  execute "docker run -d -v /var/lib/clarke-coin:/var/lib/clarke-coin -p 3000-3000:3000-3000 worace/clarke-coin:latest"
end

def docker_cleanup
  execute "docker rm -v $(docker ps --filter status=exited -q 2>/dev/null) 2>/dev/null"
  execute "docker rmi $(docker images --filter dangling=true -q 2>/dev/null) 2>/dev/null"
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
    stop_container
  end
end

desc "Start docker containers on miner nodes"
task :start do
  on DEPLOYED_NODES do |host|
    puts "Starting miner on node #{host}"
    start_container
  end
end

desc "Delete unused docker images and containers on miner nodes"
task :docker_cleanup do
  on DEPLOYED_NODES do |host|
    puts "Removing old docker images on host #{host}"
    docker_cleanup
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
    pull_image
    stop_container
    docker_cleanup
    start_container
  end
end
