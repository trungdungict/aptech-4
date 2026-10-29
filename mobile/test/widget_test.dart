import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:aptech4_mobile/main.dart';

void main() {
  testWidgets('Aptech scaffold smoke', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: MyApp()));
    expect(find.text('Aptech-4'), findsOneWidget);
  });
}
