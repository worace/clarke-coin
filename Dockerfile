FROM clojure
MAINTAINER "Horace Williams"
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
RUN lein deps
COPY . /usr/src/app
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar
VOLUME "/var/lib/clarke-coin"
EXPOSE 3000
CMD ["java", "-jar", "app-standalone.jar"]
