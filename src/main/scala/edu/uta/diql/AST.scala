package edu.uta.diql

import scala.util.parsing.input.Positional

sealed abstract class Pattern
    case class TuplePat ( components: List[Pattern] ) extends Pattern
    case class NamedPat ( name: String, pat: Pattern ) extends Pattern
    case class CallPat ( name: String, args: List[Pattern] ) extends Pattern
    case class MethodCallPat ( obj: Pattern, method: String, args: List[Pattern] ) extends Pattern
    case class StringPat ( value: String ) extends Pattern
    case class IntPat ( value: Int ) extends Pattern
    case class LongPat ( value: Long ) extends Pattern
    case class DoublePat ( value: Double ) extends Pattern
    case class BooleanPat ( value: Boolean ) extends Pattern
    case class VarPat ( name: String ) extends Pattern
    case class RestPat ( name: String ) extends Pattern
    case class StarPat () extends Pattern

sealed abstract class Qualifier
    case class Generator ( pattern: Pattern, domain: Expr ) extends Qualifier
    case class LetBinding ( pattern: Pattern, domain: Expr ) extends Qualifier
    case class Predicate ( predicate: Expr ) extends Qualifier

case class GroupByQual ( pattern: Pattern, key: Expr, having: Expr )
case class OrderByQual ( key: Expr )

case class Case ( pat: Pattern, condition: Expr, body: Expr )

sealed abstract class Expr ( var tpe: Any = null ) extends Positional  // tpe contains type information
    case class cMap ( function: Lambda, input: Expr ) extends Expr
    case class groupBy ( input: Expr ) extends Expr
    case class orderBy ( input: Expr ) extends Expr
    case class coGroup ( left: Expr, right: Expr ) extends Expr
    case class cross ( left: Expr, right: Expr ) extends Expr
    case class reduce ( monoid: String, input: Expr ) extends Expr
    case class repeat ( function: Lambda, init: Expr, n: Int ) extends Expr
    case class SelectQuery ( output: Expr, qualifiers: List[Qualifier],
                             groupBy: Option[GroupByQual],
                             orderBy: Option[OrderByQual] ) extends Expr
    case class SelectDistQuery ( output: Expr, qualifiers: List[Qualifier],
                                 groupBy: Option[GroupByQual],
                                 orderBy: Option[OrderByQual] ) extends Expr
    case class SomeQuery ( output: Expr, qualifiers: List[Qualifier] ) extends Expr
    case class AllQuery ( output: Expr, qualifiers: List[Qualifier] ) extends Expr
    case class SmallDataSet ( input: Expr ) extends Expr
    case class Lambda ( pattern: Pattern, body: Expr ) extends Expr
    case class Call ( name: String, args: List[Expr] ) extends Expr
    case class MethodCall ( obj: Expr, method: String, args: List[Expr] ) extends Expr
    case class IfE ( predicate: Expr, thenp: Expr, elsep: Expr ) extends Expr
    case class Tuple ( args: List[Expr] ) extends Expr
    case class MatchE ( expr: Expr, cases: List[Case] ) extends Expr
    case class Nth ( base: Expr, num: Int ) extends Expr
    case class Empty () extends Expr
    case class Elem ( elem: Expr ) extends Expr
    case class Merge ( left: Expr, right: Expr ) extends Expr
    case class Var ( name: String ) extends Expr
    case class StringConst ( value: String ) extends Expr
    case class IntConst ( value: Int ) extends Expr
    case class LongConst ( value: Long ) extends Expr
    case class DoubleConst ( value: Double ) extends Expr
    case class BoolConst ( value: Boolean ) extends Expr


object AST {

  var count = 0
  def newvar = { count = count+1; "_x"+count }

  def apply ( p: Pattern, f: Pattern => Pattern ): Pattern =
    p match {
      case TuplePat(ps) => TuplePat(ps.map(f(_)))
      case NamedPat(n,p) => NamedPat(n,f(p))
      case CallPat(n,ps) => CallPat(n,ps.map(f(_)))
      case MethodCallPat(o,m,ps) => MethodCallPat(f(o),m,ps.map(f(_)))
      case _ => p
    }

  def apply ( q: Qualifier, f: Expr => Expr ): Qualifier =
    q match {
      case Generator(p,x) => Generator(p,f(x))
      case LetBinding(p,x) => LetBinding(p,f(x))
      case Predicate(x) => Predicate(f(x))
    }

  def apply ( e: Expr, f: Expr => Expr ): Expr =
    { val res = e match {
      case cMap(Lambda(p,b),x) => cMap(Lambda(p,f(b)),f(x))
      case groupBy(x) => groupBy(f(x))
      case orderBy(x) => orderBy(f(x))
      case coGroup(x,y) => coGroup(f(x),f(y))
      case cross(x,y) => cross(f(x),f(y))
      case reduce(m,x) => reduce(m,f(x))
      case repeat(Lambda(p,b),x,n)
        => repeat(Lambda(p,f(b)),f(x),n)
      case SelectQuery(o,qs,gb,ob)
        => SelectQuery(f(o),qs.map(apply(_,f)),
                       gb match { case Some(GroupByQual(p,k,h))
                                        => Some(GroupByQual(p,f(k),f(h)))
                                  case x => x },
                       ob match { case Some(OrderByQual(k))
                                        => Some(OrderByQual(f(k)))
                                  case x => x })
      case SelectDistQuery(o,qs,gb,ob)
        => SelectDistQuery(f(o),qs.map(apply(_,f)),
                           gb match { case Some(GroupByQual(p,k,h))
                                        => Some(GroupByQual(p,f(k),f(h)))
                                      case x => x },
                           ob match { case Some(OrderByQual(k))
                                        => Some(OrderByQual(f(k)))
                                      case x => x })
      case SomeQuery(o,qs) => SomeQuery(f(o),qs.map(apply(_,f)))
      case AllQuery(o,qs) => AllQuery(f(o),qs.map(apply(_,f)))
      case SmallDataSet(x) => SmallDataSet(f(x))
      case Lambda(p,b) => Lambda(p,f(b))
      case Call(n,es) => Call(n,es.map(f(_)))
      case MethodCall(o,m,null) => MethodCall(f(o),m,null)
      case MethodCall(o,m,es) => MethodCall(f(o),m,es.map(f(_)))
      case IfE(p,x,y) => IfE(f(p),f(x),f(y))
      case MatchE(e,cs) => MatchE(f(e),cs.map{ case Case(p,c,b) => Case(p,f(c),f(b)) })
      case Tuple(es) => Tuple(es.map(f(_)))
      case Nth(x,n) => Nth(f(x),n)
      case Elem(x) => Elem(f(x))
      case Merge(x,y) => Merge(f(x),f(y))
      case _ => e
    }
    res.tpe = e.tpe
    res
    }

  def accumulatePat[T] ( p: Pattern, f: Pattern => T, acc: (T,T) => T, zero: T ): T =
    p match {
      case TuplePat(ps) => ps.map(f(_)).fold(zero)(acc)
      case NamedPat(n,p) => f(p)
      case CallPat(n,ps) => ps.map(f(_)).fold(zero)(acc)
      case MethodCallPat(o,m,ps) => ps.map(f(_)).fold(f(o))(acc)
      case _ => zero
    }

  def accumulateQ[T] ( q: Qualifier, f: Expr => T, acc: (T,T) => T, zero: T ): T =
    q match {
      case Generator(p,x) => f(x)
      case LetBinding(p,x) => f(x)
      case Predicate(x) => f(x)
    }

  def accumulate[T] ( e: Expr, f: Expr => T, acc: (T,T) => T, zero: T ): T =
    e match {
      case cMap(Lambda(p,b),x) => acc(f(b),f(x))
      case groupBy(x) => f(x)
      case orderBy(x) => f(x)
      case coGroup(x,y) => acc(f(x),f(y))
      case cross(x,y) => acc(f(x),f(y))
      case reduce(m,x) => f(x)
      case repeat(Lambda(p,b),x,n) => acc(f(b),f(x))
      case SelectQuery(o,qs,gb,ob)
        => acc(qs.map(accumulateQ(_,f,acc,zero)).fold(f(o))(acc),
               acc(gb match { case Some(GroupByQual(p,k,h))
                                => acc(f(k),f(h))
                              case x => zero },
                   ob match { case Some(OrderByQual(k))
                                => f(k)
                              case x => zero }))
      case SelectDistQuery(o,qs,gb,ob)
        => acc(qs.map(accumulateQ(_,f,acc,zero)).fold(f(o))(acc),
               acc(gb match { case Some(GroupByQual(p,k,h))
                                => acc(f(k),f(h))
                              case x => zero },
                   ob match { case Some(OrderByQual(k))
                                => f(k)
                              case x => zero }))
      case SomeQuery(o,qs)
        => qs.map(accumulateQ(_,f,acc,zero)).fold(f(o))(acc)
      case AllQuery(o,qs)
        => qs.map(accumulateQ(_,f,acc,zero)).fold(f(o))(acc)
      case SmallDataSet(x) => f(x)
      case Lambda(p,b) => f(b)
      case Call(n,es) => es.map(f(_)).fold(zero)(acc)
      case MethodCall(o,m,null) => f(o)
      case MethodCall(o,m,es) => es.map(f(_)).fold(f(o))(acc)
      case IfE(p,x,y) => acc(f(p),acc(f(x),f(y)))
      case MatchE(e,cs) => cs.map{ case Case(p,c,b) => acc(f(c),f(b)) }.fold(f(e))(acc)
      case Tuple(es) => es.map(f(_)).fold(zero)(acc)
      case Nth(x,n) => f(x)
      case Elem(x) => f(x)
      case Merge(x,y) => acc(f(x),f(y))
      case _ => zero
    }

  def patvars ( p: Pattern ): List[String] = 
    p match {
      case VarPat(s) => List(s)
      case RestPat(s) if (s != "_") => List(s)
      case NamedPat(n,p) => n::patvars(p)
      case _ => accumulatePat[List[String]](p,patvars(_),_++_,Nil)
    }

  def capture ( v: String, p: Pattern ): Boolean =
    p match {
      case VarPat(s) => v==s
      case RestPat(s) => v==s
      case _ => accumulatePat[Boolean](p,capture(v,_),_||_,false)
    }

  def subst ( v: String, te: Expr, e: Expr ): Expr =
    e match {
      case cMap(Lambda(p,b),x) if capture(v,p)
        => cMap(Lambda(p,b),subst(v,te,x))
      case MatchE(expr,cs)
        => MatchE(subst(v,te,expr),
                 cs.map{ case Case(p,c,b)
                            => if (capture(v,p)) Case(p,c,b)
                               else Case(p,subst(v,te,c),subst(v,te,b)) })
      case Lambda(p,b) if capture(v,p) => Lambda(p,b)
      case Var(s) => if (s==v) te else e
      case _ => apply(e,subst(v,te,_))
    }

  def subst ( from: String, to: String, p: Pattern ): Pattern =
    p match {
      case VarPat(s) if s==from => VarPat(to)
      case NamedPat(n,p) if n==from => NamedPat(to,p)
      case _ => apply(p,subst(from,to,_))
  }

  def subst ( from: Expr, to: Expr, e: Expr ): Expr =
    if (e == from) to else apply(e,subst(from,to,_))

  def occurences ( v: String, e: Expr ): Int =
    e match {
      case Var(s) => if (s==v) 1 else 0
      case cMap(Lambda(p,b),x) if capture(v,p)
        => occurences(v,x)
      case MatchE(expr,cs)
        => cs.map{ case Case(p,c,b)
                     => if (capture(v,p)) 0
                        else occurences(v,c)+occurences(v,b) }
             .fold(occurences(v,expr))(_+_)
      case Lambda(p,b) if capture(v,p) => 0
      case _ => accumulate[Int](e,occurences(v,_),_+_,0)
    }

  def occurences ( vs: List[String], e: Expr ): Int
    = vs.map(occurences(_,e)).reduce(_+_)

  def freevars ( e: Expr, except: List[String] ): List[String] =
    e match {
      case Var(s)
        => if (except.contains(s)) Nil else List(s)
      case cMap(Lambda(p,b),x)
        => freevars(b,except++patvars(p))++freevars(x,except)
      case MatchE(expr,cs)
        => cs.map{ case Case(p,c,b)
                     => val pv = except++patvars(p)
                        freevars(b,pv)++freevars(c,pv)
                     }.fold(freevars(expr,except))(_++_)
      case Lambda(p,b)
        => freevars(b,except++patvars(p))
      case _ => accumulate[List[String]](e,freevars(_,except),_++_,Nil)
    }

  def freevars ( e: Expr ): List[String] = freevars(e,Nil)

  def clean ( e: Expr ): Int = {
    e.tpe = null
    accumulate[Int](e,clean(_),_+_,0)
  }

  /** Convert a pattern to an expression */
  def toExpr ( p: Pattern ): Expr
      = p match {
        case TuplePat(ps) => Tuple(ps.map(toExpr))
        case VarPat(n) => Var(n)
        case NamedPat(_,p) => toExpr(p)
        case StringPat(s) => StringConst(s)
        case LongPat(n) => LongConst(n)
        case DoublePat(n) => DoubleConst(n)
        case BooleanPat(n) => BoolConst(n)
        case CallPat(s,ps) => Call(s,ps.map(toExpr))
        case MethodCallPat(p,m,null) => MethodCall(toExpr(p),m,null)
        case MethodCallPat(p,m,ps) => MethodCall(toExpr(p),m,ps.map(toExpr))
        case _ => Tuple(Nil)
      }
}
