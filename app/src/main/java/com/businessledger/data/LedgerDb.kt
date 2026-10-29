package com.businessledger.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.businessledger.core.Rules
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class Business(val name: String, val currency: String, val prefix: String, val allowNegative: Boolean, val scale: Int = 2)
data class Contact(val id: String, val kind: String, val name: String, val phone: String, val email: String)
data class Product(val id: String, val name: String, val category: String, val price: String, val cost: String, val stock: Boolean, val threshold: String)
data class EntryLine(val productId: String, val quantity: String, val unitPrice: String, val discount: String = "0", val taxPercent: String = "0")
data class Document(val id: String, val kind: String, val number: String?, val contactId: String, val contactName: String, val date: String, val dueDate: String, val status: String, val total: String, val paid: String)
data class DocumentLine(val productName: String, val quantity: String, val unitPrice: String, val net: String, val tax: String)
data class Movement(val productId: String, val productName: String, val date: String, val quantity: String, val reason: String)
data class Payment(val id: String, val documentId: String, val date: String, val amount: String, val method: String)
data class Expense(val id: String, val date: String, val category: String, val payee: String, val amount: String)
data class PeriodSummary(val sales: BigDecimal, val purchases: BigDecimal, val received: BigDecimal, val paid: BigDecimal, val expenses: BigDecimal, val receivables: BigDecimal, val payables: BigDecimal, val lowStock: Int, val overdue: Int)

class LedgerDb(context: Context) : SQLiteOpenHelper(context, "ledger.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE business (id TEXT PRIMARY KEY, name TEXT NOT NULL, currency TEXT NOT NULL, prefix TEXT NOT NULL, next_number INTEGER NOT NULL, allow_negative INTEGER NOT NULL, scale INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE contacts (id TEXT PRIMARY KEY, business_id TEXT NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, updated_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE products (id TEXT PRIMARY KEY, business_id TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, price TEXT NOT NULL, cost TEXT NOT NULL, track_stock INTEGER NOT NULL, threshold TEXT NOT NULL, updated_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY, business_id TEXT NOT NULL, kind TEXT NOT NULL, number TEXT, contact_id TEXT NOT NULL, date TEXT NOT NULL, due_date TEXT NOT NULL, status TEXT NOT NULL, subtotal TEXT NOT NULL, tax TEXT NOT NULL, total TEXT NOT NULL, paid TEXT NOT NULL, updated_at TEXT NOT NULL, sync_state TEXT NOT NULL, UNIQUE(business_id,kind,number))")
        db.execSQL("CREATE TABLE document_lines (id TEXT PRIMARY KEY, document_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity TEXT NOT NULL, unit_price TEXT NOT NULL, discount TEXT NOT NULL, tax_percent TEXT NOT NULL, net TEXT NOT NULL, tax TEXT NOT NULL)")
        db.execSQL("CREATE TABLE stock_movements (id TEXT PRIMARY KEY, business_id TEXT NOT NULL, product_id TEXT NOT NULL, source_key TEXT NOT NULL UNIQUE, date TEXT NOT NULL, quantity TEXT NOT NULL, reason TEXT NOT NULL)")
        db.execSQL("CREATE TABLE payments (id TEXT PRIMARY KEY, business_id TEXT NOT NULL, document_id TEXT NOT NULL, date TEXT NOT NULL, amount TEXT NOT NULL, method TEXT NOT NULL, sync_state TEXT NOT NULL)")
        db.execSQL("CREATE TABLE expenses (id TEXT PRIMARY KEY, business_id TEXT NOT NULL, date TEXT NOT NULL, category TEXT NOT NULL, payee TEXT NOT NULL, amount TEXT NOT NULL, sync_state TEXT NOT NULL)")
        db.execSQL("CREATE TABLE outbox (id TEXT PRIMARY KEY, business_id TEXT NOT NULL, entity_id TEXT NOT NULL, operation TEXT NOT NULL, state TEXT NOT NULL, created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE audit_events (id TEXT PRIMARY KEY, business_id TEXT NOT NULL, entity_id TEXT NOT NULL, action TEXT NOT NULL, occurred_at TEXT NOT NULL)")
        db.execSQL("CREATE INDEX doc_date ON documents(business_id,kind,date)")
        db.execSQL("CREATE INDEX stock_product ON stock_movements(business_id,product_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Migration required: $oldVersion to $newVersion") }
    private fun id() = UUID.randomUUID().toString()
    private fun now() = java.time.Instant.now().toString()
    private fun today() = LocalDate.now().toString()
    private fun cv(vararg pairs: Pair<String, Any?>) = ContentValues().apply { pairs.forEach { (k,v) -> when(v) { null -> putNull(k); is String -> put(k,v); is Int -> put(k,v); is Long -> put(k,v); else -> error("Unsupported value") } } }
    private fun Cursor.s(name: String) = getString(getColumnIndexOrThrow(name))
    private fun SQLiteDatabase.rows(sql: String, vararg args: String, read: (Cursor) -> Unit) { rawQuery(sql,args).use { while(it.moveToNext()) read(it) } }
    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T { val db=writableDatabase; db.beginTransaction(); try { val result=block(db); db.setTransactionSuccessful(); return result } finally { db.endTransaction() } }
    private fun event(db: SQLiteDatabase, entity: String, action: String) {
        db.insertOrThrow("outbox",null,cv("id" to id(),"business_id" to "local","entity_id" to entity,"operation" to action,"state" to "local","created_at" to now()))
        db.insertOrThrow("audit_events",null,cv("id" to id(),"business_id" to "local","entity_id" to entity,"action" to action,"occurred_at" to now()))
    }
    fun business(): Business? { var result: Business?=null; readableDatabase.rows("SELECT * FROM business WHERE id='local'") { result=Business(it.s("name"),it.s("currency"),it.s("prefix"),it.getInt(it.getColumnIndexOrThrow("allow_negative"))==1,it.getInt(it.getColumnIndexOrThrow("scale"))) }; return result }
    fun saveBusiness(value: Business) = transaction { db ->
        require(value.name.isNotBlank() && value.currency.matches(Regex("[A-Z]{3}")) && value.prefix.isNotBlank() && value.scale in 0..4)
        val existing=business()
        db.insertWithOnConflict("business",null,cv("id" to "local","name" to value.name.trim(),"currency" to value.currency,"prefix" to value.prefix.trim(),"next_number" to (if(existing==null) 1 else nextNumber(db)),"allow_negative" to if(value.allowNegative) 1 else 0,"scale" to value.scale),SQLiteDatabase.CONFLICT_REPLACE)
        event(db,"local","business.saved")
    }
    private fun nextNumber(db: SQLiteDatabase): Int { var n=1; db.rows("SELECT next_number FROM business WHERE id='local'") { n=it.getInt(0) }; return n }
    fun contacts(kind: String?=null): List<Contact> { val out=mutableListOf<Contact>(); val sql="SELECT * FROM contacts WHERE business_id='local'"+(if(kind!=null) " AND kind=?" else "")+" ORDER BY name"; readableDatabase.rows(sql,*(if(kind!=null) arrayOf(kind) else emptyArray<String>())) { out+=Contact(it.s("id"),it.s("kind"),it.s("name"),it.s("phone"),it.s("email")) }; return out }
    fun addContact(kind: String,name: String,phone: String,email: String): String = transaction { db ->
        require(kind in listOf("customer","supplier") && name.isNotBlank())
        val key=id(); db.insertOrThrow("contacts",null,cv("id" to key,"business_id" to "local","kind" to kind,"name" to name.trim(),"phone" to phone.trim(),"email" to email.trim(),"updated_at" to now())); event(db,key,"contact.created"); key
    }
    fun products(): List<Product> { val out=mutableListOf<Product>(); readableDatabase.rows("SELECT * FROM products WHERE business_id='local' ORDER BY name") { out+=Product(it.s("id"),it.s("name"),it.s("category"),it.s("price"),it.s("cost"),it.getInt(it.getColumnIndexOrThrow("track_stock"))==1,it.s("threshold")) }; return out }
    fun addProduct(name: String,category: String,price: String,cost: String,stock: Boolean,threshold: String): String = transaction { db ->
        require(name.isNotBlank()); val scale=business()?.scale ?: 2
        require(Rules.money(price,scale)>=BigDecimal.ZERO && Rules.money(cost,scale)>=BigDecimal.ZERO && Rules.decimal(threshold)>=BigDecimal.ZERO)
        val key=id(); db.insertOrThrow("products",null,cv("id" to key,"business_id" to "local","name" to name.trim(),"category" to category.trim(),"price" to Rules.money(price,scale).toPlainString(),"cost" to Rules.money(cost,scale).toPlainString(),"track_stock" to if(stock) 1 else 0,"threshold" to Rules.decimal(threshold).toPlainString(),"updated_at" to now())); event(db,key,"product.created"); key
    }
    fun documents(kind: String?=null): List<Document> { val out=mutableListOf<Document>(); val sql="SELECT d.*,c.name AS contact_name FROM documents d JOIN contacts c ON c.id=d.contact_id WHERE d.business_id='local'"+(if(kind!=null) " AND d.kind=?" else "")+" ORDER BY d.date DESC,d.updated_at DESC"; readableDatabase.rows(sql,*(if(kind!=null) arrayOf(kind) else emptyArray<String>())) { out+=Document(it.s("id"),it.s("kind"),it.s("number"),it.s("contact_id"),it.s("contact_name"),it.s("date"),it.s("due_date"),it.s("status"),it.s("total"),it.s("paid")) }; return out }
    fun documentLines(documentId: String): List<DocumentLine> { val out=mutableListOf<DocumentLine>(); readableDatabase.rows("SELECT p.name,l.quantity,l.unit_price,l.net,l.tax FROM document_lines l JOIN products p ON p.id=l.product_id WHERE l.document_id=? ORDER BY l.rowid",documentId) { out+=DocumentLine(it.s("name"),it.s("quantity"),it.s("unit_price"),it.s("net"),it.s("tax")) }; return out }
    private fun onHand(db: SQLiteDatabase, productId: String): BigDecimal { var total=BigDecimal.ZERO; db.rows("SELECT quantity FROM stock_movements WHERE business_id='local' AND product_id=?",productId) { total=total.add(BigDecimal(it.s("quantity"))) }; return total }
    fun onHand(productId: String)=onHand(readableDatabase,productId)
    fun movements(productId: String?=null): List<Movement> { val out=mutableListOf<Movement>(); val sql="SELECT m.*,p.name AS product_name FROM stock_movements m JOIN products p ON p.id=m.product_id WHERE m.business_id='local'"+(if(productId!=null) " AND m.product_id=?" else "")+" ORDER BY m.date DESC,m.rowid DESC"; readableDatabase.rows(sql,*(if(productId!=null) arrayOf(productId) else emptyArray<String>())) { out+=Movement(it.s("product_id"),it.s("product_name"),it.s("date"),it.s("quantity"),it.s("reason")) }; return out }
    fun adjustStock(productId: String,delta: String,reason: String) = transaction { db ->
        require(reason.isNotBlank()); val p=products().first { it.id==productId }; require(p.stock)
        val change=Rules.decimal(delta); require(change!=BigDecimal.ZERO)
        Rules.stock(onHand(db,productId),change,business()!!.allowNegative)
        val key=id(); db.insertOrThrow("stock_movements",null,cv("id" to key,"business_id" to "local","product_id" to productId,"source_key" to "adjust:$key","date" to today(),"quantity" to change.toPlainString(),"reason" to reason.trim())); event(db,key,"stock.adjusted")
    }
    fun saveDocument(kind: String,contactId: String,lines: List<EntryLine>,finalize: Boolean,dueDate: String): String = transaction { db ->
        require(kind in listOf("invoice","purchase") && lines.isNotEmpty()); val business=business() ?: error("Set up business first")
        val contact=contacts().firstOrNull { it.id==contactId } ?: error("Choose a contact")
        require(contact.kind==(if(kind=="invoice") "customer" else "supplier"))
        LocalDate.parse(dueDate)
        val catalog=products().associateBy { it.id }; val key=id(); var subtotal=BigDecimal.ZERO; var tax=BigDecimal.ZERO
        val computed=lines.map { line -> val p=catalog[line.productId] ?: error("Product missing"); val amount=Rules.line(Rules.decimal(line.quantity),Rules.money(line.unitPrice,business.scale),Rules.money(line.discount,business.scale),Rules.decimal(line.taxPercent),business.scale); subtotal=subtotal.add(amount.net); tax=tax.add(amount.tax); Triple(line,p,amount) }
        if(finalize && kind=="invoice") {
            val needed=computed.filter { it.second.stock }.groupBy { it.second.id }.mapValues { (_, group) -> group.fold(BigDecimal.ZERO) { acc, item -> acc.add(Rules.decimal(item.first.quantity)) } }
            needed.forEach { (productId,qty) -> Rules.stock(onHand(db,productId),qty.negate(),business.allowNegative) }
        }
        val number=if(finalize) business.prefix+nextNumber(db).toString().padStart(5,'0') else null
        val status=if(!finalize) "draft" else if(kind=="invoice") "issued" else "received"
        db.insertOrThrow("documents",null,cv("id" to key,"business_id" to "local","kind" to kind,"number" to number,"contact_id" to contactId,"date" to today(),"due_date" to dueDate,"status" to status,"subtotal" to subtotal.toPlainString(),"tax" to tax.toPlainString(),"total" to subtotal.add(tax).toPlainString(),"paid" to "0","updated_at" to now(),"sync_state" to "local"))
        computed.forEachIndexed { index,(line,p,amount) ->
            db.insertOrThrow("document_lines",null,cv("id" to id(),"document_id" to key,"product_id" to p.id,"quantity" to Rules.decimal(line.quantity).toPlainString(),"unit_price" to Rules.money(line.unitPrice,business.scale).toPlainString(),"discount" to Rules.money(line.discount,business.scale).toPlainString(),"tax_percent" to Rules.decimal(line.taxPercent).toPlainString(),"net" to amount.net.toPlainString(),"tax" to amount.tax.toPlainString()))
            if(finalize && p.stock) { val qty=Rules.decimal(line.quantity).let { if(kind=="invoice") it.negate() else it }; db.insertOrThrow("stock_movements",null,cv("id" to id(),"business_id" to "local","product_id" to p.id,"source_key" to "doc:$key:$index","date" to today(),"quantity" to qty.toPlainString(),"reason" to "$kind $number")) }
        }
        if(finalize) db.execSQL("UPDATE business SET next_number=next_number+1 WHERE id='local'")
        event(db,key,if(finalize) "$kind.finalized" else "$kind.drafted"); key
    }
    fun finalizeDraft(documentId: String) = transaction { db ->
        val doc=documents().first { it.id==documentId }; require(doc.status=="draft")
        val policy=business() ?: error("Business missing")
        val lines=mutableListOf<Pair<String,BigDecimal>>()
        db.rows("SELECT l.product_id,l.quantity,p.track_stock FROM document_lines l JOIN products p ON p.id=l.product_id WHERE l.document_id=?",documentId) {
            if(it.getInt(2)==1) lines+=it.s("product_id") to BigDecimal(it.s("quantity"))
        }
        if(doc.kind=="invoice") lines.groupBy { it.first }.forEach { (productId,items) ->
            val quantity=items.fold(BigDecimal.ZERO) { acc,item -> acc.add(item.second) }
            Rules.stock(onHand(db,productId),quantity.negate(),policy.allowNegative)
        }
        val number=policy.prefix+nextNumber(db).toString().padStart(5,'0')
        db.execSQL("UPDATE documents SET number=?,status=?,updated_at=?,sync_state='local' WHERE id=?",arrayOf(number,if(doc.kind=="invoice") "issued" else "received",now(),documentId))
        lines.forEachIndexed { index,(productId,quantity) ->
            val change=if(doc.kind=="invoice") quantity.negate() else quantity
            db.insertOrThrow("stock_movements",null,cv("id" to id(),"business_id" to "local","product_id" to productId,"source_key" to "doc:$documentId:$index","date" to today(),"quantity" to change.toPlainString(),"reason" to "${doc.kind} $number"))
        }
        db.execSQL("UPDATE business SET next_number=next_number+1 WHERE id='local'")
        event(db,documentId,"${doc.kind}.finalized")
    }
    fun payments(): List<Payment> { val out=mutableListOf<Payment>(); readableDatabase.rows("SELECT * FROM payments WHERE business_id='local' ORDER BY date DESC,rowid DESC") { out+=Payment(it.s("id"),it.s("document_id"),it.s("date"),it.s("amount"),it.s("method")) }; return out }
    fun addPayment(documentId: String,amount: String,method: String): String = transaction { db ->
        val doc=documents().first { it.id==documentId }; require(doc.status!="draft")
        val scale=business()!!.scale; val value=Rules.money(amount,scale); val remaining=Rules.allocation(BigDecimal(doc.total).subtract(BigDecimal(doc.paid)),value)
        val key=id(); db.insertOrThrow("payments",null,cv("id" to key,"business_id" to "local","document_id" to documentId,"date" to today(),"amount" to value.toPlainString(),"method" to method,"sync_state" to "local"))
        db.execSQL("UPDATE documents SET paid=?,status=?,updated_at=?,sync_state='local' WHERE id=?",arrayOf(BigDecimal(doc.paid).add(value).toPlainString(),if(remaining==BigDecimal.ZERO) "paid" else "partially paid",now(),documentId))
        event(db,key,"payment.recorded"); event(db,documentId,"document.payment_applied"); key
    }
    fun expenses(): List<Expense> { val out=mutableListOf<Expense>(); readableDatabase.rows("SELECT * FROM expenses WHERE business_id='local' ORDER BY date DESC,rowid DESC") { out+=Expense(it.s("id"),it.s("date"),it.s("category"),it.s("payee"),it.s("amount")) }; return out }
    fun addExpense(category: String,payee: String,amount: String): String = transaction { db ->
        require(category.isNotBlank()); val value=Rules.money(amount,business()!!.scale); require(value>BigDecimal.ZERO)
        val key=id(); db.insertOrThrow("expenses",null,cv("id" to key,"business_id" to "local","date" to today(),"category" to category.trim(),"payee" to payee.trim(),"amount" to value.toPlainString(),"sync_state" to "local")); event(db,key,"expense.created"); key
    }
    fun summary(from: LocalDate,to: LocalDate): PeriodSummary {
        val docs=documents(); val pays=payments(); val costs=expenses(); val current=today()
        fun sum(values: Iterable<String>)=values.fold(BigDecimal.ZERO) { acc,s -> acc.add(BigDecimal(s)) }
        val periodDocs=docs.filter { it.status!="draft" && it.date>=from.toString() && it.date<=to.toString() }
        val periodPay=pays.filter { it.date>=from.toString() && it.date<=to.toString() }; val kinds=docs.associateBy { it.id }
        val asOf=docs.filter { it.status!="draft" && it.date<=current }
        val low=products().count { it.stock && onHand(it.id)<=BigDecimal(it.threshold) }
        return PeriodSummary(sum(periodDocs.filter { it.kind=="invoice" }.map { it.total }),sum(periodDocs.filter { it.kind=="purchase" }.map { it.total }),sum(periodPay.filter { kinds[it.documentId]?.kind=="invoice" }.map { it.amount }),sum(periodPay.filter { kinds[it.documentId]?.kind=="purchase" }.map { it.amount }),sum(costs.filter { it.date>=from.toString() && it.date<=to.toString() }.map { it.amount }),sum(asOf.filter { it.kind=="invoice" }.map { BigDecimal(it.total).subtract(BigDecimal(it.paid)).toPlainString() }),sum(asOf.filter { it.kind=="purchase" }.map { BigDecimal(it.total).subtract(BigDecimal(it.paid)).toPlainString() }),low,asOf.count { it.kind=="invoice" && it.dueDate<current && BigDecimal(it.total)>BigDecimal(it.paid) })
    }
    fun pendingChanges(): Int { var count=0; readableDatabase.rows("SELECT COUNT(*) AS n FROM outbox WHERE state='local'") { count=it.getInt(0) }; return count }
}
