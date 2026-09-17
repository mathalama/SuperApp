import 'package:dio/dio.dart';
import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';
import '../models/kyc_response_model.dart';

abstract class IKycRemoteDataSource {
  Future<KycResponseModel> submitKyc({
    required String documentType,
    required String frontImagePath,
    required String selfieImagePath,
    String? backImagePath,
  });

  Future<KycResponseModel> getMyKyc();
}

class KycRemoteDataSourceImpl implements IKycRemoteDataSource {
  final ApiClient _apiClient;

  KycRemoteDataSourceImpl({required ApiClient apiClient}) : _apiClient = apiClient;

  @override
  Future<KycResponseModel> submitKyc({
    required String documentType,
    required String frontImagePath,
    required String selfieImagePath,
    String? backImagePath,
  }) async {
    try {
      final formDataMap = <String, dynamic>{
        'documentType': documentType,
        'documentFront': await MultipartFile.fromFile(
          frontImagePath,
          filename: 'document_front.jpg',
        ),
        'selfie': await MultipartFile.fromFile(
          selfieImagePath,
          filename: 'selfie.jpg',
        ),
      };

      if (backImagePath != null && backImagePath.isNotEmpty) {
        formDataMap['documentBack'] = await MultipartFile.fromFile(
          backImagePath,
          filename: 'document_back.jpg',
        );
      }

      final formData = FormData.fromMap(formDataMap);

      final res = await _apiClient.dio.post(
        ApiConstants.kycVerify,
        data: formData,
        options: Options(
          sendTimeout: ApiConstants.kycUploadTimeout,
          receiveTimeout: ApiConstants.kycUploadTimeout,
        ),
      );

      return KycResponseModel.fromJson(res.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.handleError(e);
    }
  }

  @override
  Future<KycResponseModel> getMyKyc() async {
    try {
      final res = await _apiClient.dio.get(ApiConstants.kycMe);
      return KycResponseModel.fromJson(res.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.handleError(e);
    }
  }
}
