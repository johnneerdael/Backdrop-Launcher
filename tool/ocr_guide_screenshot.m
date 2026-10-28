#import <Foundation/Foundation.h>
#import <Vision/Vision.h>

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        if (argc != 2) return 2;
        VNRecognizeTextRequest *request = [VNRecognizeTextRequest new];
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate;
        request.recognitionLanguages = @[@"en-US"];
        request.usesLanguageCorrection = NO;
        NSURL *url = [NSURL fileURLWithPath:[NSString stringWithUTF8String:argv[1]]];
        VNImageRequestHandler *handler = [[VNImageRequestHandler alloc] initWithURL:url options:@{}];
        NSError *error = nil;
        if (![handler performRequests:@[request] error:&error]) {
            fprintf(stderr, "%s\n", error.localizedDescription.UTF8String);
            return 1;
        }
        NSMutableArray *rows = [NSMutableArray array];
        for (VNRecognizedTextObservation *observation in request.results) {
            VNRecognizedText *text = [observation topCandidates:1].firstObject;
            if (!text) continue;
            CGRect box = observation.boundingBox;
            [rows addObject:@{@"text":text.string, @"confidence":@(text.confidence),
                @"x":@(box.origin.x), @"y":@(box.origin.y),
                @"width":@(box.size.width), @"height":@(box.size.height)}];
        }
        NSData *data = [NSJSONSerialization dataWithJSONObject:rows options:NSJSONWritingSortedKeys error:&error];
        if (!data) return 1;
        [[NSFileHandle fileHandleWithStandardOutput] writeData:data];
    }
    return 0;
}
