# ClarkeCoin


![Build Status](https://api.travis-ci.org/worace/clarke-coin.svg)

What would it take to make a simplistic but functional crypto currency?

## Running the Docker Image

**OS X** (With docker-machine)

```
docker-machine start default
docker build -t clarke-coin .
docker run -v /var/lib/clarke-coin:/var/lib/clarke-coin -p 3000-3000:3000-3000/tcp 69cdd323db31
```

With this running you can connect to the docker-machine VM and check out the data directory:

```
docker-machine ssh
ls /var/lib/clarke-coin
```

## Deploying

First, make sure you are logged in to docker hub

```
sudo docker login
```

```
sudo docker build -t worace/clarke-coin .
sudo docker push worace/clarke-coin:latest
ssh root@159.203.204.18
# these will be run on the host machine
docker pull worace/clarke-coin:latest
docker ps -q --filter ancestor=worace/clarke-coin | xargs docker stop
docker run -d -v /var/lib/clarke-coin:/var/lib/clarke-coin -p 3000-3000:3000-3000 worace/clarke-coin:latest
```

Except you would need to replace `worace` with your dockerhub username
and replace IP addrs with your IP.

## License

Copyright © 2016 Horace Williams

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

