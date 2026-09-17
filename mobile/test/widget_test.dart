import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:app/core/di/injection_container.dart';
import 'package:app/domain/entities/user_entity.dart';
import 'package:app/domain/repositories/i_auth_repository.dart';
import 'package:app/domain/repositories/i_kyc_repository.dart';
import 'package:app/main.dart';

class MockAuthRepo extends Mock implements IAuthRepository {}
class MockKycRepo extends Mock implements IKycRepository {}

void main() {
  late MockAuthRepo mockAuthRepo;
  late MockKycRepo mockKycRepo;

  setUp(() {
    mockAuthRepo = MockAuthRepo();
    mockKycRepo = MockKycRepo();

    if (sl.isRegistered<IAuthRepository>()) {
      sl.unregister<IAuthRepository>();
    }
    if (sl.isRegistered<IKycRepository>()) {
      sl.unregister<IKycRepository>();
    }

    sl.registerLazySingleton<IAuthRepository>(() => mockAuthRepo);
    sl.registerLazySingleton<IKycRepository>(() => mockKycRepo);
  });

  testWidgets('Renders AuthScreen when user is unauthenticated', (WidgetTester tester) async {
    when(() => mockAuthRepo.isAuthenticated()).thenAnswer((_) async => false);
    when(() => mockAuthRepo.getCurrentUser()).thenAnswer((_) async => null);

    await tester.pumpWidget(const SuperAppKycApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('SuperApp KYC'), findsWidgets);
    expect(find.text('Sign In'), findsNWidgets(2)); // Tab and Button
    expect(find.text('Create Account'), findsOneWidget);
  });

  testWidgets('Renders HomeKycFlowScreen when user is authenticated', (WidgetTester tester) async {
    const user = UserEntity(
      id: 'usr-1',
      username: 'johndoe',
      email: 'john@example.com',
    );

    when(() => mockAuthRepo.isAuthenticated()).thenAnswer((_) async => true);
    when(() => mockAuthRepo.getCurrentUser()).thenAnswer((_) async => user);

    await tester.pumpWidget(const SuperAppKycApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('johndoe'), findsOneWidget);
    expect(find.text('Scan Government ID'), findsOneWidget);
  });
}
