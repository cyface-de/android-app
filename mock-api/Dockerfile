FROM node:alpine

WORKDIR /app

# Install mock-api dependencies
COPY . /app
RUN npm install

# Launch mock-api
CMD npm start
EXPOSE 9113
