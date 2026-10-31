import IdentityLookup
import QoriqchiCore

/// SMS filtri: notanish raqamdan kelgan xabarda firibgar yoki virusli havola bo'lsa — "Junk" bo'limiga.
/// iOS bu kengaytmaga internetni ham, xabarni saqlashni ham ruxsat bermaydi: tekshiruv faqat telefonda.
final class MessageFilterExtension: ILMessageFilterExtension {}

extension MessageFilterExtension: ILMessageFilterQueryHandling {
    func handle(
        _ queryRequest: ILMessageFilterQueryRequest,
        context: ILMessageFilterExtensionContext,
        completion: @escaping (ILMessageFilterQueryResponse) -> Void
    ) {
        let response = ILMessageFilterQueryResponse()
        response.action = ILMessageFilterAction.none
        if let body = queryRequest.messageBody {
            let sets = SharedStore.loadSets()
            if SmsRules.verdict(for: body, lookup: { Blocklist.lookup(sets, host: $0) }) != nil {
                response.action = .junk
            }
        }
        completion(response)
    }
}
