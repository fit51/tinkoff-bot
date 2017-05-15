# tinkoff-bot
Telegram Bot with IB Tinkoff partial functionality created as part of the Tinkoff Fintech School.
Created by pakondratyuk and VecnaSecrets

# What can it do:
- Authorize user via phone number in Tinkoff
- Get user's balance and 10 last card operations
- Get current rates for different currencies
 
# Commands to interact with bot:
- /rates or /r - to get currency rates
- /balance or /b - to get current balance
- /history or /h - to get last operations
- /help or /h - for help

# Build
sbt assembly    - produces fat tinkoff-bot.jar

# Run Singleton mode
java -jar tinkoff-bot.jar

# Run Cluster mode
Requires:
    at least 1 master node and 2 worker nodes
    seed nodes are configured on 127.0.0.1:2551 - 2553
About:
    TelegramUpdater - is singleton Actor running on master nodes
    SessionManager and NoSessionActions are deployed on worker nodes by Router Pools

How to:
Start Master:
    java -jar tinkoff-bot.jar master    (by-default start on 127.0.0.1:2551)

Start Workers:
    java -jar tinkoff-bot.jar worker       starts on 127.0.0.1 and random port
    java -jar tinkoff-bot.jar worker       starts on 127.0.0.1 and random port

# Additional config
You can control host, port, seed params through Java System Properties
Example:
    java -jar -DHOST=host1 -DPORT=5551 tinkoff-bot.jar master
    java -jar -Dakka.cluster.seed-nodes.0=akka.tcp://botsystem@host1:5551 tinkoff-bot.jar worker





