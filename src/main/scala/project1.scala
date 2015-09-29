import java.security.MessageDigest
import scala.collection.mutable.ArrayBuffer
import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.routing.RoundRobinRouter
import scala.util.Random
import akka.actor.ActorSystem


//BOSS assigns work to Workers at Server machine
case class SpawnWork(leading_zeros: Integer)

//This will start the mining at Worker Akka Actor
case class Mine(leading_zeros: Integer)

//Worker will send total inputs processed to BOSS
case class MiningCompleted(bitCoinsGenerated:Integer)

//Worker sends found Bitcoins to BOSS
case class CoinsFound(coins: ArrayBuffer[String])

//Remote worker message
case class Remote()

//Implementation of BOSS Akka Actor at Server
class Boss extends Actor {

  private var totalWorkers:Integer = 0
  private var totalBitcoins:Integer = 0
  private var leadingZeros:Integer = 0
  private var trackWorkersReported:Integer=0
  private var totalBitCoinsFound:ArrayBuffer[String]=  ArrayBuffer[String]()

  def receive = {

    case SpawnWork(leading_zeros: Integer) =>
      leadingZeros = leading_zeros
      val noOfWorker : Integer = 5*Runtime.getRuntime().availableProcessors()
      totalWorkers=noOfWorker
      val workers = context.actorOf(Props[Worker].withRouter(RoundRobinRouter(nrOfInstances= noOfWorker)))   // TBD
      for (i <- 1 to noOfWorker)
        workers! Mine(leadingZeros)
      println("System Boot complete... Worker started for 5 mins ")

    case CoinsFound(coins : ArrayBuffer[String]) =>
     //println("Received Coins from Worker")
     totalBitCoinsFound++=coins

    case MiningCompleted(bitCoinsGenerated: Integer) =>
     trackWorkersReported+=1
     totalBitcoins+=bitCoinsGenerated
     if(trackWorkersReported==totalWorkers)
     {
       println("Number of workers reported : "+trackWorkersReported)
       println("Number of inputs processed : "+totalBitcoins)
       totalBitCoinsFound=totalBitCoinsFound.distinct    //TBD
       for(i<- 0 to totalBitCoinsFound.length-1)
        println(totalBitCoinsFound(i))

       println("Number of bitcoins found : "+totalBitCoinsFound.length )
       context.system.shutdown()
    }

    case Remote() =>
     totalWorkers+=1
     sender ! Mine(leadingZeros)

  }
}

class Worker extends Actor {

    var bitcoinArray : ArrayBuffer[String] = ArrayBuffer[String]()
    val startTime = System.currentTimeMillis()
    var noOfInputsProcessed:Integer=0
    def receive = {
      case Mine(leading_zeros: Integer) =>
        while (System.currentTimeMillis() - startTime < 300000) {
          val gatorId: String = "amarnitdgp"
          val inputText: String = gatorId + Random.alphanumeric.take(10).mkString +Random.alphanumeric.take(5).mkString
          val sha = MessageDigest.getInstance("SHA-256")
          sha.update(inputText.getBytes("UTF-8"))
          val digest = sha.digest()
          val hash = new StringBuffer()

          for (j <- 0 to digest.length - 1) {
            val hex = Integer.toHexString(0xff & digest(j))
            if (hex.length() == 1) hash.append('0')
            hash.append(hex)
          }

          val extracted_val: String = hash.substring(0, leading_zeros)
          val comparison_val = "0" * leading_zeros
          if (extracted_val.equals(comparison_val)) {
            bitcoinArray += inputText + " " + hash
          }

          noOfInputsProcessed += 1
          if (noOfInputsProcessed % 1000000 == 0) {
            sender ! CoinsFound(bitcoinArray)
          }
        }
        sender ! MiningCompleted(noOfInputsProcessed)


      case ipaddr :String =>
        val master=context.actorSelection("akka.tcp://MasterSystem@"+ipaddr+":5152/user/Boss")
        master! Remote()

    }
}

object project1 extends App{

  var input :String=args(0)

  if(input.length > 6)
  {
    println("Assigning work to remote Worker ...")
    val ip : String = input
    val noOfWorker : Integer = 2*Runtime.getRuntime().availableProcessors()
    val worker =ActorSystem("WorkerSystem").actorOf(Props[Worker].withRouter(RoundRobinRouter(nrOfInstances= noOfWorker)))
    for (n <- 1 to noOfWorker)
      worker ! ip
  }
  else
  {
    println("System Boot ...  ")
    var no_of_zeros=args(0).toInt
    val System = ActorSystem("MasterSystem")
    val boss = System.actorOf(Props[Boss],name="Boss")
    boss ! SpawnWork(no_of_zeros)


  }
}

