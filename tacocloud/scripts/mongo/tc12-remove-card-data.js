// TC-12: detecta y elimina datos de tarjeta heredados en la base del laboratorio.
// Uso: mongo "mongodb://localhost:27017/test" scripts/mongo/tc12-remove-card-data.js
// (la aplicación ejecuta lo mismo al arrancar con LegacyPaymentDataMigration).
var legacy = { $or: [ { ccNumber: { $exists: true } }, { ccCVV: { $exists: true } }, { ccExpiration: { $exists: true } } ] };
["tacoOrder", "paymentMethod"].forEach(function (collection) {
  var found = db.getCollection(collection).countDocuments(legacy);
  var result = db.getCollection(collection).updateMany(legacy, { $unset: { ccNumber: "", ccCVV: "", ccExpiration: "" } });
  print(collection + ": " + found + " documentos con datos de tarjeta, " + result.modifiedCount + " limpiados");
});
