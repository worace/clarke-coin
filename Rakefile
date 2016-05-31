require 'sshkit'
require 'sshkit/dsl'
include SSHKit::DSL

DEPLOYED_NODES = ["159.203.206.61", "159.203.206.63", "159.203.206.49"].map do |ip|
  SSHKit::Host.new("root@#{ip}")
end

task :deploy do
  sh "sudo docker build -t worace/clarke-coin ."
  sh "sudo docker push worace/clarke-coin:latest"

  on DEPLOYED_NODES.take(1) do |host|
    puts "Deploying to node #{host}"
    execute "docker pull worace/clarke-coin:latest"
    execute "docker ps -ql | xargs docker stop"
    execute "docker run -d -v /var/lib/clarke-coin:/var/lib/clarke-coin -p 3000-3000:3000-3000 worace/clarke-coin:latest"
  end
end
