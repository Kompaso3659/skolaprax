import scala.reflect.ClassTag

trait TraitAddMemberOrNo {
  val additionalClassMember: String = "additionalClassMember"
}
case class ClassAddMemberOrNo () extends TraitAddMemberOrNo

trait PrintableClass {
  def classAddMemberOrNo: ClassAddMemberOrNo
  def printClass: Unit = {
    println ("ClassAddMemberOrNo: " + classAddMemberOrNo.additionalClassMember)
  }
}

case class A (a: Int, b: String, classAddMemberOrNo: ClassAddMemberOrNo)
    extends TraitAddMemberOrNo
    with PrintableClass {

  def printParents (): Unit = {
    printParentInternal [A]
    printParentInternal [TraitAddMemberOrNo]
    printParentInternal [ClassAddMemberOrNo]

  }

  private def printParentInternal [P] (implicit ev: ClassTag [P]): Unit = {
    val parentName = ev.runtimeClass.getSimpleName
    println (s"Ancestor: $parentName")
  }
}

val a = A (1, "test", ClassAddMemberOrNo ())
a.printClass
a.printParents () 
