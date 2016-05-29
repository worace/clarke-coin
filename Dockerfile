FROM clojure
MAINTAINER "Horace Williams"
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
RUN lein deps
COPY . /usr/src/app
RUN lein uberjar
RUN mv /usr/src/app/target/clarke-coin-node.jar .
VOLUME "/var/lib/clarke-coin"
EXPOSE 3000
CMD ["java", "-jar", "clarke-coin-node.jar", "-r", "7889", "-d", "dns1.clarkecoin.org"]
