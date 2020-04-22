package io.chrisdavenport.epimetheus.redis4cats

import cats._
import cats.implicits._
import cats.effect._
import dev.profunktor.redis4cats.algebra._

import java.util.concurrent.TimeUnit
import scala.concurrent.TimeoutException
import cats.effect.ExitCase.Canceled
import cats.effect.ExitCase.Completed
import RedisMetricOps.TerminationType

object RedisMetrics {

  def middleware[F[_]: Sync: Clock, K, V](
    commands: StringCommands[F, K, V] with HashCommands[F, K, V]
      with SetCommands[F, K, V]
      with SortedSetCommands[F, K, V]
      with ListCommands[F, K, V]
      with GeoCommands[F, K, V]
      with ConnectionCommands[F]
      with ServerCommands[F, K]
      with TransactionalCommands[F, K]
      with PipelineCommands[F]
      with ScriptCommands[F, K, V]
      with KeyCommands[F, K], 
    ops: RedisMetricOps[F],
    classifier: Option[String] = None
  ): StringCommands[F, K, V] with HashCommands[F, K, V]
    with SetCommands[F, K, V]
    with SortedSetCommands[F, K, V]
    with ListCommands[F, K, V]
    with GeoCommands[F, K, V]
    with ConnectionCommands[F]
    with ServerCommands[F, K]
    with TransactionalCommands[F, K]
    with PipelineCommands[F]
    with ScriptCommands[F, K, V]
    with KeyCommands[F, K] = {

    val clock = Clock[F]

    def registerCompletion(start: Long)(e: ExitCase[Throwable]): F[Unit] =
      clock
        .monotonic(TimeUnit.NANOSECONDS)
        .flatMap { now =>
          e match {
            case Canceled => 
              ops.recordTotalTime(TerminationType.Canceled, now - start, classifier)
            case Completed =>
              ops.recordTotalTime(TerminationType.Success, now - start, classifier)
            case cats.effect.ExitCase.Error(e) =>
              if (e.isInstanceOf[TimeoutException]) {
                ops.recordTotalTime(TerminationType.Timeout, now - start, classifier)
              } else {
                ops.recordTotalTime(TerminationType.Error(e), now - start, classifier)
              }
          }
          
        }

    val transform: F ~> F = new ~>[F, F]{
      def apply[A](fa: F[A]): F[A] = ops.active(classifier).use{_ => 
        Bracket[F, Throwable].bracketCase(clock.monotonic(TimeUnit.NANOSECONDS))(_ => fa)(registerCompletion(_)(_))
      }
    }

    new MapKCommands(commands, transform)
  }

  private class MapKCommands[G[_], F[_], K, V](
    commands: StringCommands[G, K, V] with HashCommands[G, K, V]
      with SetCommands[G, K, V]
      with SortedSetCommands[G, K, V]
      with ListCommands[G, K, V]
      with GeoCommands[G, K, V]
      with ConnectionCommands[G]
      with ServerCommands[G, K]
      with TransactionalCommands[G, K]
      with PipelineCommands[G]
      with ScriptCommands[G, K, V]
      with KeyCommands[G, K], 
    transform: G ~> F
  ) extends StringCommands[F, K, V]
    with HashCommands[F, K, V]
    with SetCommands[F, K, V]
    with SortedSetCommands[F, K, V]
    with ListCommands[F, K, V]
    with GeoCommands[F, K, V]
    with ConnectionCommands[F]
    with ServerCommands[F, K]
    with TransactionalCommands[F, K]
    with PipelineCommands[F]
    with ScriptCommands[F, K, V]
    with KeyCommands[F, K]{
    // Members declared in dev.profunktor.redis4cats.algebra.AutoFlush
    def disableAutoFlush: F[Unit]  = transform(commands.disableAutoFlush)
    def enableAutoFlush: F[Unit] = transform(commands.enableAutoFlush)
    def flushCommands: F[Unit] = transform(commands.flushCommands)
    
    // Members declared in dev.profunktor.redis4cats.algebra.Bits
    def bitCount(key: K, start: Long, end: Long): F[Long] = transform(commands.bitCount(key, start, end))
    def bitCount(key: K): F[Long] = transform(commands.bitCount(key))
    def bitOpAnd(destination: K, sources: K*): F[Unit] = transform(commands.bitOpAnd(destination, sources:_*))
    def bitOpNot(destination: K, source: K): F[Unit] = transform(commands.bitOpNot(destination, source))
    def bitOpOr(destination: K, sources: K*): F[Unit] = transform(commands.bitOpOr(destination, sources:_*))

    def bitOpXor(destination: K, sources: K*): F[Unit] = transform(commands.bitOpXor(destination, sources:_*))
    def bitPos(key: K, state: Boolean, start: Long, end: Long): F[Long] = transform(commands.bitPos(key, state, start, end))
    def bitPos(key: K, state: Boolean, start: Long): F[Long] = transform(commands.bitPos(key, state, start))
    def bitPos(key: K, state: Boolean): F[Long] = transform(commands.bitPos(key, state))
    
    // Members declared in dev.profunktor.redis4cats.algebra.Decrement
    def decr(key: K)(implicit N: Numeric[V]): F[Long] = transform(commands.decr(key))
    def decrBy(key: K, amount: Long)(implicit N: Numeric[V]): F[Long] = transform(commands.decrBy(key, amount))
    
    // Members declared in dev.profunktor.redis4cats.algebra.Diagnostic
    def dbsize: F[Long] = transform(commands.dbsize)
    def info: F[Map[String,String]] = transform(commands.info)
    def lastSave: F[java.time.Instant] = transform(commands.lastSave)
    def slowLogLen: F[Long] = transform(commands.slowLogLen)
    
    // Members declared in dev.profunktor.redis4cats.algebra.Flush
    def flushAll: F[Unit] = transform(commands.flushAll)
    def flushAllAsync: F[Unit] = transform(commands.flushAllAsync)
    def keys(key: K): F[List[K]] = transform(commands.keys(key))
    
    // Members declared in dev.profunktor.redis4cats.algebra.GeoGetter
    def geoDist(key: K, from: V, to: V, unit: io.lettuce.core.GeoArgs.Unit): F[Double] = 
      transform(commands.geoDist(key, from, to, unit))
    def geoHash(key: K, values: V*): F[List[Option[String]]] = 
      transform(commands.geoHash(key, values:_*))
    def geoPos(key: K, values: V*): F[List[dev.profunktor.redis4cats.effects.GeoCoordinate]] = 
      transform(commands.geoPos(key, values:_*))
    def geoRadius(key: K, geoRadius: dev.profunktor.redis4cats.effects.GeoRadius, unit: io.lettuce.core.GeoArgs.Unit, args: io.lettuce.core.GeoArgs): F[List[dev.profunktor.redis4cats.effects.GeoRadiusResult[V]]] = 
      transform(commands.geoRadius(key, geoRadius, unit, args))
    def geoRadius(key: K, geoRadius: dev.profunktor.redis4cats.effects.GeoRadius, unit: io.lettuce.core.GeoArgs.Unit): F[Set[V]] = 
      transform(commands.geoRadius(key, geoRadius, unit))
    def geoRadiusByMember(key: K, value: V, dist: dev.profunktor.redis4cats.effects.Distance, unit: io.lettuce.core.GeoArgs.Unit, args: io.lettuce.core.GeoArgs): F[List[dev.profunktor.redis4cats.effects.GeoRadiusResult[V]]] = 
      transform(commands.geoRadiusByMember(key, value, dist, unit, args))
    def geoRadiusByMember(key: K, value: V, dist: dev.profunktor.redis4cats.effects.Distance, unit: io.lettuce.core.GeoArgs.Unit): F[Set[V]] = 
      transform(commands.geoRadiusByMember(key, value, dist, unit))
    
    // Members declared in dev.profunktor.redis4cats.algebra.GeoSetter
    def geoAdd(key: K, geoValues: dev.profunktor.redis4cats.effects.GeoLocation[V]*): F[Unit] = 
      transform(commands.geoAdd(key, geoValues:_*))
    def geoRadius(key: K, geoRadius: dev.profunktor.redis4cats.effects.GeoRadius, unit: io.lettuce.core.GeoArgs.Unit, storage: dev.profunktor.redis4cats.effects.GeoRadiusDistStorage[K]): F[Unit] = 
      transform(commands.geoRadius(key, geoRadius, unit, storage))
    def geoRadius(key: K, geoRadius: dev.profunktor.redis4cats.effects.GeoRadius, unit: io.lettuce.core.GeoArgs.Unit, storage: dev.profunktor.redis4cats.effects.GeoRadiusKeyStorage[K]): F[Unit] = 
      transform(commands.geoRadius(key, geoRadius, unit, storage))
    def geoRadiusByMember(key: K, value: V, dist: dev.profunktor.redis4cats.effects.Distance, unit: io.lettuce.core.GeoArgs.Unit, storage: dev.profunktor.redis4cats.effects.GeoRadiusDistStorage[K]): F[Unit] = 
      transform(commands.geoRadiusByMember(key, value, dist, unit, storage))
    def geoRadiusByMember(key: K, value: V, dist: dev.profunktor.redis4cats.effects.Distance, unit: io.lettuce.core.GeoArgs.Unit, storage: dev.profunktor.redis4cats.effects.GeoRadiusKeyStorage[K]): F[Unit] = 
      transform(commands.geoRadiusByMember(key, value, dist, unit, storage))
    
    // Members declared in dev.profunktor.redis4cats.algebra.Getter
    def get(key: K): F[Option[V]] = transform(commands.get(key))
    def getBit(key: K, offset: Long): F[Option[Long]] = transform(commands.getBit(key, offset))
    def getRange(key: K, start: Long, end: Long): F[Option[V]] = transform(commands.getRange(key, start, end))
    def strLen(key: K): F[Option[Long]] = transform(commands.strLen(key))
    
    // Members declared in dev.profunktor.redis4cats.algebra.HashCommands
    def hDel(key: K, fields: K*): F[Unit] = transform(commands.hDel(key, fields:_*))
    def hExists(key: K, field: K): F[Boolean] = transform(commands.hExists(key, field))
    
    // Members declared in dev.profunktor.redis4cats.algebra.HashGetter
    def hGet(key: K, field: K): F[Option[V]] = transform(commands.hGet(key, field))
    def hGetAll(key: K): F[Map[K,V]] = transform(commands.hGetAll(key))
    def hKeys(key: K): F[List[K]] = transform(commands.hKeys(key))
    def hLen(key: K): F[Option[Long]] = transform(commands.hLen(key))
    def hStrLen(key: K, field: K): F[Option[Long]] = transform(commands.hStrLen(key, field))
    def hVals(key: K): F[List[V]] = transform(commands.hVals(key))
    def hmGet(key: K, fields: K*): F[Map[K,V]] = transform(commands.hmGet(key, fields:_*))
    
    // Members declared in dev.profunktor.redis4cats.algebra.HashIncrement
    def hIncrBy(key: K, field: K, amount: Long)(implicit N: Numeric[V]): F[Long] = 
      transform(commands.hIncrBy(key, field, amount))
    def hIncrByFloat(key: K, field: K, amount: Double)(implicit N: Numeric[V]): F[Double] = 
      transform(commands.hIncrByFloat(key, field, amount))
    
    // Members declared in dev.profunktor.redis4cats.algebra.HashSetter
    def hSet(key: K, field: K, value: V): F[Unit] = transform(commands.hSet(key, field, value))
    def hSetNx(key: K, field: K, value: V): F[Boolean] = transform(commands.hSetNx(key, field, value))
    def hmSet(key: K, fieldValues: Map[K,V]): F[Unit] = transform(commands.hmSet(key, fieldValues))
    
    // Members declared in dev.profunktor.redis4cats.algebra.Increment
    def incr(key: K)(implicit N: Numeric[V]): F[Long] = transform(commands.incr(key))
    def incrBy(key: K, amount: Long)(implicit N: Numeric[V]): F[Long] = transform(commands.incrBy(key, amount))
    def incrByFloat(key: K, amount: Double)(implicit N: Numeric[V]): F[Double] = transform(commands.incrByFloat(key, amount))
    
    // Members declared in dev.profunktor.redis4cats.algebra.KeyCommands
    def del(key: K*): F[Unit] = transform(commands.del(key:_*))
    def exists(key: K*): F[Boolean] = transform(commands.exists(key:_*))
    def expire(k: K, seconds: scala.concurrent.duration.FiniteDuration): F[Unit] = 
      transform(commands.expire(k, seconds))
    def pttl(key: K): F[Option[scala.concurrent.duration.FiniteDuration]] = 
      transform(commands.pttl(key))
    def ttl(key: K): F[Option[scala.concurrent.duration.FiniteDuration]] = 
      transform(commands.ttl(key))
    
    // Members declared in dev.profunktor.redis4cats.algebra.ListBlocking
    def blPop(timeout: scala.concurrent.duration.Duration, keys: K*): F[(K, V)] = 
      transform(commands.blPop(timeout, keys:_*))
    def brPop(timeout: scala.concurrent.duration.Duration, keys: K*): F[(K, V)] = 
      transform(commands.brPop(timeout, keys:_*))
    def brPopLPush(timeout: scala.concurrent.duration.Duration, source: K, destination: K): F[Option[V]] = 
      transform(commands.brPopLPush(timeout, source, destination))
    
    // Members declared in dev.profunktor.redis4cats.algebra.ListGetter
    def lIndex(key: K, index: Long): F[Option[V]] = transform(commands.lIndex(key, index))
    def lLen(key: K): F[Option[Long]] = transform(commands.lLen(key))
    def lRange(key: K, start: Long, stop: Long): F[List[V]] = transform(commands.lRange(key, start, stop))
    
    // Members declared in dev.profunktor.redis4cats.algebra.ListPushPop
    def lPop(key: K): F[Option[V]] = transform(commands.lPop(key))
    def lPush(key: K, values: V*): F[Unit] = transform(commands.lPush(key, values:_*))
    def lPushX(key: K, values: V*): F[Unit] = transform(commands.lPushX(key, values:_*))
    def rPop(key: K): F[Option[V]] = transform(commands.rPop(key))
    def rPopLPush(source: K, destination: K): F[Option[V]] = transform(commands.rPopLPush(source, destination))
    def rPush(key: K, values: V*): F[Unit] = transform(commands.rPush(key, values:_*))
    def rPushX(key: K, values: V*): F[Unit] = transform(commands.rPushX(key, values:_*))
    
    // Members declared in dev.profunktor.redis4cats.algebra.ListSetter
    def lInsertAfter(key: K, pivot: V, value: V): F[Unit] = transform(commands.lInsertAfter(key, pivot, value))
    def lInsertBefore(key: K, pivot: V, value: V): F[Unit] = transform(commands.lInsertBefore(key, pivot, value))
    def lRem(key: K, count: Long, value: V): F[Unit] = transform(commands.lRem(key, count, value))
    def lSet(key: K, index: Long, value: V): F[Unit] = transform(commands.lSet(key, index, value))
    def lTrim(key: K, start: Long, stop: Long): F[Unit] = transform(commands.lTrim(key, start, stop))
    
    // Members declared in dev.profunktor.redis4cats.algebra.MultiKey
    def mGet(keys: Set[K]): F[Map[K,V]] = transform(commands.mGet(keys))
    def mSet(keyValues: Map[K,V]): F[Unit] = transform(commands.mSet(keyValues))
    def mSetNx(keyValues: Map[K,V]): F[Boolean] = transform(commands.mSetNx(keyValues))
    
    // Members declared in dev.profunktor.redis4cats.algebra.Ping
    def ping: F[String] = transform(commands.ping)
    
    // Members declared in dev.profunktor.redis4cats.algebra.Scripting
    def eval(script: String, output: dev.profunktor.redis4cats.effects.ScriptOutputType[V], keys: List[K], values: List[V]): F[output.R] = 
      transform(commands.eval(script, output, keys, values))
    def eval(script: String, output: dev.profunktor.redis4cats.effects.ScriptOutputType[V], keys: List[K]): F[output.R] = 
      transform(commands.eval(script, output, keys))
    def eval(script: String, output: dev.profunktor.redis4cats.effects.ScriptOutputType[V]): F[output.R] = 
      transform(commands.eval(script, output))
    def evalSha(script: String, output: dev.profunktor.redis4cats.effects.ScriptOutputType[V], keys: List[K], values: List[V]): F[output.R] = 
      transform(commands.evalSha(script, output, keys, values))
    def evalSha(script: String, output: dev.profunktor.redis4cats.effects.ScriptOutputType[V], keys: List[K]): F[output.R] = 
      transform(commands.evalSha(script, output, keys))
    def evalSha(script: String, output: dev.profunktor.redis4cats.effects.ScriptOutputType[V]): F[output.R] = 
      transform(commands.evalSha(script, output))
    def scriptExists(digests: String*): F[List[Boolean]] = transform(commands.scriptExists(digests:_*))
    def scriptFlush: F[Unit] = transform(commands.scriptFlush)
    def scriptLoad(script: V): F[String] = transform(commands.scriptLoad(script))
    
    // Members declared in dev.profunktor.redis4cats.algebra.SetCommands
    def sIsMember(key: K, value: V): F[Boolean] = transform(commands.sIsMember(key, value))
    
    // Members declared in dev.profunktor.redis4cats.algebra.SetDeletion
    def sPop(key: K, count: Long): F[Set[V]] = transform(commands.sPop(key, count))
    def sPop(key: K): F[Option[V]] = transform(commands.sPop(key))
    def sRem(key: K, values: V*): F[Unit] = transform(commands.sRem(key, values:_*))
    
    // Members declared in dev.profunktor.redis4cats.algebra.SetGetter
    def sCard(key: K): F[Long] = transform(commands.sCard(key))
    def sDiff(keys: K*): F[Set[V]] = transform(commands.sDiff(keys:_*))
    def sInter(keys: K*): F[Set[V]] = transform(commands.sInter(keys:_*))
    def sMembers(key: K): F[Set[V]] = transform(commands.sMembers(key))
    def sRandMember(key: K, count: Long): F[List[V]] = transform(commands.sRandMember(key, count))
    def sRandMember(key: K): F[Option[V]] = transform(commands.sRandMember(key))
    def sUnion(keys: K*): F[Set[V]] = transform(commands.sUnion(keys:_*))
    def sUnionStore(destination: K, keys: K*): F[Unit] = transform(commands.sUnionStore(destination, keys:_*))
    
    // Members declared in dev.profunktor.redis4cats.algebra.SetSetter
    def sAdd(key: K, values: V*): F[Unit] = transform(commands.sAdd(key, values:_*))
    def sDiffStore(destination: K, keys: K*): F[Unit] = transform(commands.sDiffStore(destination, keys:_*))
    def sInterStore(destination: K, keys: K*): F[Unit] = transform(commands.sInterStore(destination, keys:_*))
    def sMove(source: K, destination: K, value: V): F[Unit] = transform(commands.sMove(source, destination, value))
    
    // Members declared in dev.profunktor.redis4cats.algebra.Setter
    def append(key: K, value: V): F[Unit] = transform(commands.append(key, value))
    def getSet(key: K, value: V): F[Option[V]] = transform(commands.getSet(key, value))
    def set(key: K, value: V, setArgs: dev.profunktor.redis4cats.effects.SetArgs): F[Boolean] = 
      transform(commands.set(key, value, setArgs))
    def set(key: K, value: V): F[Unit] = transform(commands.set(key, value))
    def setEx(key: K, value: V, expiresIn: scala.concurrent.duration.FiniteDuration): F[Unit] = 
      transform(commands.setEx(key, value, expiresIn))
    def setNx(key: K, value: V): F[Boolean] = 
      transform(commands.setNx(key, value))
    def setRange(key: K, value: V, offset: Long): F[Unit] = 
      transform(commands.setRange(key, value, offset))
    
    // Members declared in dev.profunktor.redis4cats.algebra.SortedSetGetter
    def zCard(key: K): F[Option[Long]] = transform(commands.zCard(key))
    def zCount(key: K, range: dev.profunktor.redis4cats.effects.ZRange[V])(implicit ev: Numeric[V]): F[Option[Long]] = 
      transform(commands.zCount(key, range))
    def zLexCount(key: K, range: dev.profunktor.redis4cats.effects.ZRange[V]): F[Option[Long]] = 
      transform(commands.zLexCount(key, range))
    def zRange(key: K, start: Long, stop: Long): F[List[V]] = 
      transform(commands.zRange(key, start, stop))
    def zRangeByLex(key: K, range: dev.profunktor.redis4cats.effects.ZRange[V], limit: Option[dev.profunktor.redis4cats.effects.RangeLimit]): F[List[V]] = 
      transform(commands.zRangeByLex(key, range, limit))
    def zRangeByScore[T](key: K, range: dev.profunktor.redis4cats.effects.ZRange[T], limit: Option[dev.profunktor.redis4cats.effects.RangeLimit])(implicit evidence$1: Numeric[T]): F[List[V]] = 
      transform(commands.zRangeByScore(key, range, limit))
    def zRangeByScoreWithScores[T](key: K, range: dev.profunktor.redis4cats.effects.ZRange[T], limit: Option[dev.profunktor.redis4cats.effects.RangeLimit])(implicit evidence$2: Numeric[T]): F[List[dev.profunktor.redis4cats.effects.ScoreWithValue[V]]] = 
      transform(commands.zRangeByScoreWithScores(key, range, limit))
    def zRangeWithScores(key: K, start: Long, stop: Long): F[List[dev.profunktor.redis4cats.effects.ScoreWithValue[V]]] = 
      transform(commands.zRangeWithScores(key, start, stop))
    def zRank(key: K, value: V): F[Option[Long]] = transform(commands.zRank(key, value))
    def zRevRange(key: K, start: Long, stop: Long): F[List[V]] = transform(commands.zRevRange(key, start, stop))
    def zRevRangeByLex(key: K, range: dev.profunktor.redis4cats.effects.ZRange[V], limit: Option[dev.profunktor.redis4cats.effects.RangeLimit]): F[List[V]] = 
      transform(commands.zRevRangeByLex(key, range, limit))
    def zRevRangeByScore[T](key: K, range: dev.profunktor.redis4cats.effects.ZRange[T], limit: Option[dev.profunktor.redis4cats.effects.RangeLimit])(implicit evidence$3: Numeric[T]): F[List[V]] = 
      transform(commands.zRevRangeByScore(key, range, limit))
    def zRevRangeByScoreWithScores[T](key: K, range: dev.profunktor.redis4cats.effects.ZRange[T], limit: Option[dev.profunktor.redis4cats.effects.RangeLimit])(implicit evidence$4: Numeric[T]): F[List[dev.profunktor.redis4cats.effects.ScoreWithValue[V]]] = 
      transform(commands.zRevRangeByScoreWithScores(key, range, limit))
    def zRevRangeWithScores(key: K, start: Long, stop: Long): F[List[dev.profunktor.redis4cats.effects.ScoreWithValue[V]]] = 
      transform(commands.zRevRangeWithScores(key, start, stop))
    def zRevRank(key: K, value: V): F[Option[Long]] = transform(commands.zRevRank(key, value))
    def zScore(key: K, value: V): F[Option[Double]] = transform(commands.zScore(key, value))
    
    // Members declared in dev.profunktor.redis4cats.algebra.SortedSetSetter
    def zAdd(key: K, args: Option[io.lettuce.core.ZAddArgs], values: dev.profunktor.redis4cats.effects.ScoreWithValue[V]*): F[Unit] = 
      transform(commands.zAdd(key, args, values:_*))
    def zAddIncr(key: K, args: Option[io.lettuce.core.ZAddArgs], value: dev.profunktor.redis4cats.effects.ScoreWithValue[V]): F[Unit] = 
      transform(commands.zAddIncr(key, args, value))
    def zIncrBy(key: K, member: V, amount: Double): F[Unit] = 
      transform(commands.zIncrBy(key, member, amount))
    def zInterStore(destination: K, args: Option[io.lettuce.core.ZStoreArgs], keys: K*): F[Unit] = 
      transform(commands.zInterStore(destination, args, keys:_*))
    def zRem(key: K, values: V*): F[Unit] = transform(commands.zRem(key, values:_*))
    def zRemRangeByLex(key: K, range: dev.profunktor.redis4cats.effects.ZRange[V]): F[Unit] = 
      transform(commands.zRemRangeByLex(key, range))
    def zRemRangeByRank(key: K, start: Long, stop: Long): F[Unit] = 
      transform(commands.zRemRangeByRank(key, start, stop))
    def zRemRangeByScore(key: K, range: dev.profunktor.redis4cats.effects.ZRange[V])(implicit ev: Numeric[V]): F[Unit] = 
      transform(commands.zRemRangeByScore(key, range))
    def zUnionStore(destination: K, args: Option[io.lettuce.core.ZStoreArgs], keys: K*): F[Unit] = 
      transform(commands.zUnionStore(destination, args, keys:_*))
    
    // Members declared in dev.profunktor.redis4cats.algebra.Transaction
    def discard: F[Unit] = transform(commands.discard)
    def exec: F[Unit] = transform(commands.exec)
    def multi: F[Unit] = transform(commands.multi)
    
    // Members declared in dev.profunktor.redis4cats.algebra.Watcher
    def unwatch: F[Unit] = transform(commands.unwatch)
    def watch(keys: K*): F[Unit] = transform(commands.watch(keys:_*))
  }

}