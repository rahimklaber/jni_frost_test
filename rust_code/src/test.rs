use std::collections::HashMap;

use bitcoin_serai::crypto::BitcoinHram;
use dkg::frost::Commitments;
use k256::elliptic_curve::sec1::ToEncodedPoint;
use modular_frost::{algorithm::{SchnorrSignature, Hram}, curve::Secp256k1};
use secp256k1::{Message, hashes::{sha256, Hash}};
use sha2::{Sha256, Digest};

use crate::{SchnorrKeyGenWrapper, ResultKeygen1, ResultKeygen2, SchnorrSignWrapper, SignParams2};
// use crate::SchnorrWrapper;

#[test]
fn test() {


    let machine1 = SchnorrKeyGenWrapper::new(2, 2, 1, "test".into());
    let machine2 = SchnorrKeyGenWrapper::new(2, 2, 2, "test".into());

    let res1m1 = SchnorrKeyGenWrapper::key_gen_1_create_commitments(machine1);
    let machine1 = res1m1.keygen;
    let commitments_1 = res1m1.res;
    let res1m2 = SchnorrKeyGenWrapper::key_gen_1_create_commitments(machine2);
    let machine2 = res1m2.keygen;
    let commitments_2 = res1m2.res;

    let res2m1 = SchnorrKeyGenWrapper::key_gen_2_generate_shares(machine1, crate::ParamsKeygen2 { commitment_user_indices: vec![2], commitments: vec![commitments_2.clone()] });
    let machine1 = res2m1.keygen;
    let res2m2 = SchnorrKeyGenWrapper::key_gen_2_generate_shares(machine2, crate::ParamsKeygen2 { commitment_user_indices: vec![1], commitments: vec![commitments_1.clone()] });
    let machine2 = res2m2.keygen;

    let shares_for_1 = res2m2.shares.get(0).unwrap().iter().map(|&x| x as i8).collect();
    let res3m1 = SchnorrKeyGenWrapper::key_gen_3_complete(machine1, crate::ParamsKeygen3 { shares_user_indices: vec![2], shares: vec![shares_for_1]});

    let shares_for_2 = res2m1.shares.get(0).unwrap().iter().map(|&x| x as i8).collect();
    let res3m2 = SchnorrKeyGenWrapper::key_gen_3_complete(machine2, crate::ParamsKeygen3 { shares_user_indices: vec![1], shares: vec![shares_for_2]});

    let sign_wrapper_1 = SchnorrSignWrapper::new_instance_for_signing(&res3m1);
    let sign_wrapper_2 = SchnorrSignWrapper::new_instance_for_signing(&res3m2);

    let sign_res_1_m_1 = SchnorrSignWrapper::sign_1_preprocess(sign_wrapper_1);
    let sign_wrapper_1 = sign_res_1_m_1.wrapper;

    let sign_res_1_m_2 = SchnorrSignWrapper::sign_1_preprocess(sign_wrapper_2);
    let sign_wrapper_2 = sign_res_1_m_2.wrapper;

    const MESSAGE: &'static [u8] = b"Hello, World!";
    let digest = &Sha256::digest(MESSAGE);
    let digest_u8: &[u8]= digest;
    let digest_i8 = unsafe { &*(digest_u8 as *const _  as *const [i8]) };
    let mut param_m1 = SignParams2{ commitments: HashMap::default()};
    param_m1.add_commitment_from_user(2, &sign_res_1_m_2.preprocess.iter().map(|&x| x as i8).collect::<Vec<i8>>()[..]);
    let sign_res_2_m_1 = SchnorrSignWrapper::sign_2_sign(sign_wrapper_1, param_m1, digest_i8);

    let mut param_m2 = SignParams2{ commitments: HashMap::default()};
    param_m2.add_commitment_from_user(1, &sign_res_1_m_1.preprocess.iter().map(|&x| x as i8).collect::<Vec<i8>>()[..]);
    let sign_res_2_m_2 = SchnorrSignWrapper::sign_2_sign(sign_wrapper_2, param_m2, digest_i8);

    let sign_wrapper_1 = sign_res_2_m_1.wrapper;

    let mut param_comlete = crate::SignParams3::new();
    param_comlete.add_share_of_user(2, &sign_res_2_m_2.share.iter().map(|&x| x as i8).collect::<Vec<i8>>()[..]);
    let sig = SchnorrSignWrapper::sign_3_complete(sign_wrapper_1, param_comlete);


    let sig_buf: Vec<u8> = sig.iter().map(|&x| x as u8).collect();
    let sig_obj = secp256k1::schnorr::Signature::from_slice(&sig_buf.as_ref()).unwrap();

    let pubkey_compressed = res3m1.key.group_key().to_encoded_point(true);
    let pubkey =
    secp256k1::XOnlyPublicKey::from_slice(&pubkey_compressed.x().to_owned().unwrap()).unwrap();

    let msg = Message::from(sha256::Hash::hash(&MESSAGE));

    //would error if not valid
    let _res = sig_obj.verify(&msg, &pubkey).unwrap();
    // let challenge = BitcoinHram::hram(&sigObj.R, &res3m1.key.group_key(), &[1]);

    // let verify = sigObj.verify(res3m1.key.group_key(), challenge);


    // let (machine1,commitments_1) = machine1.key_gen_1_create_commitments();
    // let (machine2,commitments_2) = machine2.key_gen_1_create_commitments();

    // let commitments_for_1 = vec![(2,commitments_2.clone())];
    // let commitments_for_2 = vec![(1,commitments_1.clone())];

    // let (machine1, shares1) = machine1.key_gen_2_generate_shares(commitments_for_1);
    // let (machine2, shares2) = machine2.key_gen_2_generate_shares(commitments_for_2);

    // println!("size: {:?}",shares2.len());
    
    // let shares_for_1 = vec![(2,shares2.into_iter().find(|x| true).unwrap().1)];
    // let shares_for_2 = vec![(1,shares1.into_iter().find(|x| true).unwrap().1)];

    // println!("share for 1 : {:?}",shares_for_1);

    // let sig_wrapper1 = machine1.key_gen_3_complete(shares_for_1);
    // let sig_wrapper2 = machine2.key_gen_3_complete(shares_for_2);


    // let (machine1,commitments_1) = machine1.key_gen_1_create_commitments();


}   